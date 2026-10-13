package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.examples.baseline.common.AutoscalingConfig;
import org.cloudbus.cloudsim.examples.baseline.common.broker.DynamicCloudletBroker;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * CloudSim-backed environment for the first plain Discrete AdvSAC baseline.
 *
 * CloudSim is advanced in a dedicated thread and paused at each RL boundary.
 * This lets the agent choose an action every 10 simulated seconds without
 * letting the agent manipulate CloudSim internals directly.
 */
public final class AdvsacCloudSimEnvironment implements AdvsacEnvironment {

    private static final long PAUSE_WAIT_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 2L;

    private final AdvsacConfig.WorkloadModel workloadModel;
    private final AdvsacStateBuilder stateBuilder = new AdvsacStateBuilder();
    private final AdvsacRewardCalculator rewardCalculator = new AdvsacRewardCalculator();

    private List<Vm> activeVms;
    private List<Cloudlet> cloudlets;
    private List<Double> arrivalTimes;
    private DynamicCloudletBroker broker;
    private Thread simulationThread;
    private int nextVmId;
    private double currentTime;
    private boolean episodeDone;
    private boolean lastConstraintViolation;
    private int totalScaleUpOperations;
    private int totalScaleDownOperations;

    public AdvsacCloudSimEnvironment() {
        this(AdvsacConfig.WORKLOAD_MODEL);
    }

    public AdvsacCloudSimEnvironment(final AdvsacConfig.WorkloadModel workloadModel) {
        if (workloadModel == null) {
            throw new IllegalArgumentException("A workload model is required.");
        }
        this.workloadModel = workloadModel;
    }

    @Override
    public AdvsacState reset() {
        if (simulationThread != null && simulationThread.isAlive()) {
            throw new IllegalStateException(
                    "Cannot reset while the previous CloudSim episode is still running.");
        }

        try {
            simulationThread = null;
            CloudSim.init(1, Calendar.getInstance(), false);

            activeVms = new ArrayList<>();
            cloudlets = new ArrayList<>();
            arrivalTimes = new ArrayList<>();
            currentTime = 0.0;
            episodeDone = false;
            lastConstraintViolation = false;
            totalScaleUpOperations = 0;
            totalScaleDownOperations = 0;
            stateBuilder.reset();

            Datacenter datacenter = createDatacenter("Advsac_Datacenter");
            broker = new DynamicCloudletBroker("Advsac_Broker", AdvsacConfig.EPISODE_DURATION + 1.0);

            for (int vmId = 0; vmId < AdvsacConfig.INITIAL_VM_COUNT; vmId++) {
                activeVms.add(createVm(vmId));
            }
            nextVmId = activeVms.size();
            AdvsacWorkloadGenerator generator =
                    new AdvsacWorkloadGenerator(AdvsacConfig.RANDOM_SEED);
            cloudlets = generator.generate(
                    broker,
                    broker.getId(),
                    workloadModel);
            arrivalTimes = new ArrayList<>(generator.getArrivalTimes());

            // Ensure a simulation event exists exactly at the episode boundary.
            new EpisodeTicker("Advsac_Episode_Ticker");
            CloudSim.terminateSimulation(AdvsacConfig.EPISODE_DURATION);

            /*
             * Return S0 before starting CloudSim. The first action is applied
             * to the initial VM pool, then the simulation advances from t=0
             * to t=10. This preserves exactly 100 ten-second transitions.
             */
            currentTime = 0.0;
            return observe(currentTime);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reset the AdvSAC CloudSim environment.",
                    exception);
        }
    }

    @Override
    public AdvsacTransition step(final int actionIndex) {
        if (episodeDone) {
            throw new IllegalStateException("The episode is complete. Call reset() first.");
        }
        if (actionIndex < 0 || actionIndex >= AdvsacConfig.ACTION_DIMENSION) {
            throw new IllegalArgumentException("Action index must be in [0,4].");
        }

        int vmDelta = AdvsacConfig.actionValueFromIndex(actionIndex);
        lastConstraintViolation = false;
        double nextTime;

        if (simulationThread == null) {
            // First action is applied before VM creation, so scale-in is safe.
            applyInitialScalingAction(vmDelta);
            broker.submitGuestList(activeVms);

            nextTime = AdvsacConfig.RL_TIMESTEP;
            if (!CloudSim.pauseSimulation((long) nextTime)) {
                throw new IllegalStateException(
                        "Could not schedule initial CloudSim pause at t=" + nextTime);
            }

            simulationThread = new Thread(
                    () -> CloudSim.startSimulation(),
                    "Advsac-CloudSim-episode");
            simulationThread.setDaemon(true);
            simulationThread.start();
            waitForPauseOrCompletion(nextTime);
            currentTime = nextTime;

            if (!CloudSim.isPaused()) {
                episodeDone = true;
                throw new IllegalStateException(
                        "CloudSim completed before the first RL pause at t="
                                + nextTime + " seconds.");
            }
        } else {
            if (!CloudSim.isPaused()) {
                throw new IllegalStateException(
                        "CloudSim must be paused at an RL decision boundary.");
            }

            applyScalingAction(vmDelta);
            nextTime = Math.min(
                    currentTime + AdvsacConfig.RL_TIMESTEP,
                    AdvsacConfig.EPISODE_DURATION);

            if (nextTime >= AdvsacConfig.EPISODE_DURATION) {
                CloudSim.resumeSimulation();
                joinSimulationThread();
                currentTime = AdvsacConfig.EPISODE_DURATION;
                episodeDone = true;
            } else {
                if (!CloudSim.pauseSimulation((long) nextTime)) {
                    throw new IllegalStateException(
                            "Could not schedule CloudSim pause at t=" + nextTime);
                }
                CloudSim.resumeSimulation();
                waitForPauseOrCompletion(nextTime);
                currentTime = nextTime;
                if (!CloudSim.isPaused()) {
                    episodeDone = true;
                }
            }
        }

        AdvsacState nextState = observe(currentTime);
        double reward = rewardCalculator.calculate(
                stateBuilder.getLastAverageCpuUtilization(),
                stateBuilder.getLastAverageResponseTime(),
                activeVms.size(),
                lastConstraintViolation);

        return new AdvsacTransition(
                nextState,
                reward,
                episodeDone,
                actionIndex,
                currentTime);
    }

    public int getTotalScaleUpOperations() {
        return totalScaleUpOperations;
    }

    public int getTotalScaleDownOperations() {
        return totalScaleDownOperations;
    }

    public int getActiveVmCount() {
        return activeVms == null ? 0 : activeVms.size();
    }

    /**
     * Returns IDs of active VMs that are not fully provisioned in CloudSim.
     *
     * Checking the active list size against the broker's created-list size is
     * not sufficient: those lists have different lifecycle semantics. Each
     * active VM must instead have a host and appear in the broker's ACK list.
     */
    public List<Integer> getUnprovisionedActiveVmIds() {
        List<Integer> unprovisionedIds = new ArrayList<>();
        if (activeVms == null || broker == null) {
            return unprovisionedIds;
        }

        List<Vm> acknowledgedVms = broker.getGuestsCreatedList();
        for (Vm vm : activeVms) {
            if (vm.getHost() == null || !acknowledgedVms.contains(vm)) {
                unprovisionedIds.add(vm.getId());
            }
        }
        return unprovisionedIds;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    private AdvsacState observe(final double observationTime) {
        return stateBuilder.build(
                observationTime,
                activeVms,
                cloudlets,
                arrivalTimes,
                broker.getCloudletList().size());
    }

    /**
     * Applies the first action before the CloudSim broker has created any VMs.
     */
    private int applyInitialScalingAction(final int requestedDelta) {
        if (requestedDelta > 0) {
            if (activeVms.size() + requestedDelta > AdvsacConfig.MAX_VM_COUNT) {
                lastConstraintViolation = true;
            }
            int additions = Math.min(
                    requestedDelta,
                    AdvsacConfig.MAX_VM_COUNT - activeVms.size());
            for (int i = 0; i < additions; i++) {
                activeVms.add(createVm(nextVmId++));
            }
            if (additions > 0) {
                totalScaleUpOperations++;
            }
            return additions;
        }

        if (requestedDelta < 0) {
            if (activeVms.size() + requestedDelta < AdvsacConfig.MIN_VM_COUNT) {
                lastConstraintViolation = true;
            }
            int removals = Math.min(
                    -requestedDelta,
                    activeVms.size() - AdvsacConfig.MIN_VM_COUNT);
            for (int i = 0; i < removals; i++) {
                activeVms.remove(activeVms.size() - 1);
            }
            if (removals > 0) {
                totalScaleDownOperations++;
            }
            return -removals;
        }

        return 0;
    }

    /**
     * Returns the actual number of VMs added/removed. Scale-in only removes
     * idle VMs to avoid terminating requests that are still running.
     */
    private int applyScalingAction(final int requestedDelta) {
        if (requestedDelta > 0) {
            if (activeVms.size() + requestedDelta > AdvsacConfig.MAX_VM_COUNT) {
                lastConstraintViolation = true;
            }
            int additions = Math.min(
                    requestedDelta,
                    AdvsacConfig.MAX_VM_COUNT - activeVms.size());
            List<Vm> newVms = new ArrayList<>(additions);
            for (int i = 0; i < additions; i++) {
                Vm vm = createVm(nextVmId++);
                activeVms.add(vm);
                newVms.add(vm);
            }

            // Submit the entire scale-out as one broker event. Sending one event
            // per VM can make DatacenterBrokerEX request the same not-yet-acked
            // VM more than once when several events are processed at one time.
            if (!newVms.isEmpty()) {
                broker.createVmsAfter(
                        newVms,
                        AdvsacConfig.PROVISIONING_DELAY_SECONDS);
                totalScaleUpOperations++;
            }
            return additions;
        }

        if (requestedDelta < 0) {
            if (activeVms.size() + requestedDelta < AdvsacConfig.MIN_VM_COUNT) {
                lastConstraintViolation = true;
            }
            int removalsRequested = Math.min(
                    -requestedDelta,
                    activeVms.size() - AdvsacConfig.MIN_VM_COUNT);
            List<Vm> vmsToRemove = new ArrayList<>(removalsRequested);

            for (int i = activeVms.size() - 1;
                    i >= 0 && vmsToRemove.size() < removalsRequested;
                    i--) {
                Vm candidate = activeVms.get(i);
                if (candidate.getHost() != null
                        && candidate.getTotalUtilizationOfCpu(currentTime) <= 0.001
                        && !hasUnfinishedCloudletAssignedTo(candidate)) {
                    vmsToRemove.add(candidate);
                }
            }

            /*
             * Exclude selected VMs from broker scheduling BEFORE sending the
             * destroy event. A cloudlet-arrival event at the same simulation
             * timestamp must not be assigned to a VM that is being removed.
             */
            if (!vmsToRemove.isEmpty()) {
                for (Vm vm : vmsToRemove) {
                    broker.excludeVmFromScheduling(vm);
                }
                broker.destroyVMsAfter(vmsToRemove, 0.0);
                activeVms.removeAll(vmsToRemove);
                totalScaleDownOperations++;
            }
            return -vmsToRemove.size();
        }

        return 0;
    }

    /**
     * A VM is eligible for scale-in only when no unfinished cloudlet is
     * assigned to it. CPU utilization alone is insufficient because a cloudlet
     * can be queued or between scheduling events while utilization reads zero.
     */
    private boolean hasUnfinishedCloudletAssignedTo(final Vm vm) {
        for (Cloudlet cloudlet : cloudlets) {
            if (cloudlet.getGuestId() == vm.getId() && !cloudlet.isFinished()) {
                return true;
            }
        }
        return false;
    }

    private Vm createVm(final int vmId) {
        return new Vm(
                vmId,
                broker.getId(),
                AutoscalingConfig.VM_MIPS,
                AutoscalingConfig.VM_PES,
                AutoscalingConfig.VM_RAM,
                AutoscalingConfig.VM_BW,
                AutoscalingConfig.VM_SIZE,
                "Xen",
                new CloudletSchedulerTimeShared());
    }

    private Datacenter createDatacenter(final String name) throws Exception {
        List<Host> hosts = new ArrayList<>();

        for (int hostId = 0; hostId < AutoscalingConfig.NUMBER_OF_HOSTS; hostId++) {
            List<Pe> peList = new ArrayList<>();
            for (int peId = 0; peId < AutoscalingConfig.HOST_PES; peId++) {
                peList.add(new Pe(
                        peId,
                        new PeProvisionerSimple(AutoscalingConfig.HOST_MIPS)));
            }

            hosts.add(new Host(
                    hostId,
                    new RamProvisionerSimple(AutoscalingConfig.HOST_RAM),
                    new BwProvisionerSimple(AutoscalingConfig.HOST_BW),
                    AutoscalingConfig.HOST_STORAGE,
                    peList,
                    new VmSchedulerTimeShared(peList)));
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86",
                "Linux",
                "Xen",
                hosts,
                5.5,
                AutoscalingConfig.COST_PER_CPU,
                AutoscalingConfig.COST_PER_MEMORY,
                AutoscalingConfig.COST_PER_STORAGE,
                AutoscalingConfig.COST_PER_BW);

        return new Datacenter(
                name,
                characteristics,
                new VmAllocationPolicySimple(hosts),
                new LinkedList<Storage>(),
                0);
    }


    /**
     * Keeps the event queue alive and creates an event exactly at the
     * configured episode boundary, avoiding CloudSim processing a later
     * event before applying its termination cutoff.
     */
    private static final class EpisodeTicker extends SimEntity {

        private enum TickerTag implements CloudSimTags {
            TICK
        }

        private EpisodeTicker(final String name) throws Exception {
            super(name);
        }

        @Override
        public void startEntity() {
            schedule(getId(), AdvsacConfig.RL_TIMESTEP, TickerTag.TICK);
        }

        @Override
        public void processEvent(final SimEvent event) {
            double nextTick = CloudSim.clock() + AdvsacConfig.RL_TIMESTEP;
            if (nextTick <= AdvsacConfig.EPISODE_DURATION) {
                schedule(getId(), AdvsacConfig.RL_TIMESTEP, TickerTag.TICK);
            }
        }

        @Override
        public void shutdownEntity() {
            // No external resources to release.
        }
    }

    private void waitForPauseOrCompletion(final double targetTime) {
        long deadline = System.currentTimeMillis() + PAUSE_WAIT_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (CloudSim.isPaused()) {
                return;
            }
            if (simulationThread != null && !simulationThread.isAlive()) {
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for CloudSim pause.",
                        exception);
            }
        }

        throw new IllegalStateException(
                "Timed out waiting for CloudSim to pause at t=" + targetTime + " seconds.");
    }

    private void joinSimulationThread() {
        if (simulationThread == null) {
            return;
        }
        try {
            simulationThread.join(PAUSE_WAIT_TIMEOUT_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for episode completion.",
                    exception);
        }
        if (simulationThread.isAlive()) {
            throw new IllegalStateException("CloudSim did not finish within the expected time.");
        }
    }
}
