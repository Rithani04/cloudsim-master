package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.examples.baseline.common.AutoscalingConfig;
import org.cloudbus.cloudsim.examples.baseline.common.broker.DynamicCloudletBroker;
import org.cloudbus.cloudsim.core.CloudSim;
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
    private int datacenterId;
    private int nextVmId;
    private double currentTime;
    private boolean episodeDone;
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
            CloudSim.init(1, Calendar.getInstance(), false);

            activeVms = new ArrayList<>();
            cloudlets = new ArrayList<>();
            arrivalTimes = new ArrayList<>();
            currentTime = 0.0;
            episodeDone = false;
            totalScaleUpOperations = 0;
            totalScaleDownOperations = 0;

            Datacenter datacenter = createDatacenter("Advsac_Datacenter");
            datacenterId = datacenter.getId();
            broker = new DynamicCloudletBroker("Advsac_Broker");

            for (int vmId = 0; vmId < AdvsacConfig.INITIAL_VM_COUNT; vmId++) {
                activeVms.add(createVm(vmId));
            }
            nextVmId = activeVms.size();
            broker.submitGuestList(activeVms);

            AdvsacWorkloadGenerator generator =
                    new AdvsacWorkloadGenerator(AdvsacConfig.RANDOM_SEED);
            cloudlets = generator.generate(
                    broker,
                    broker.getId(),
                    workloadModel);
            arrivalTimes = new ArrayList<>(generator.getArrivalTimes());

            CloudSim.terminateSimulation(AdvsacConfig.EPISODE_DURATION);

            /*
             * Start with a fully initialized observation at t=10. The initial
             * VM creation and broker startup events therefore have time to run.
             */
            currentTime = AdvsacConfig.RL_TIMESTEP;
            CloudSim.pauseSimulation((long) currentTime);
            simulationThread = new Thread(
                    () -> CloudSim.startSimulation(),
                    "Advsac-CloudSim-episode");
            simulationThread.setDaemon(true);
            simulationThread.start();

            waitForPauseOrCompletion(currentTime);
            if (!CloudSim.isPaused()) {
                episodeDone = true;
                throw new IllegalStateException(
                        "CloudSim completed before the first RL pause at t="
                                + currentTime + " seconds.");
            }

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
        if (!CloudSim.isPaused()) {
            throw new IllegalStateException("CloudSim must be paused at an RL decision boundary.");
        }

        int vmDelta = AdvsacConfig.actionValueFromIndex(actionIndex);
        int appliedDelta = applyScalingAction(vmDelta);

        double nextTime = Math.min(
                currentTime + AdvsacConfig.RL_TIMESTEP,
                AdvsacConfig.EPISODE_DURATION);

        if (nextTime >= AdvsacConfig.EPISODE_DURATION) {
            /*
             * CloudSim's termination cutoff takes precedence over a pause
             * at the same time, so let the final interval run to completion.
             */
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

        AdvsacState nextState = observe(currentTime);
        double reward = rewardCalculator.calculate(
                stateBuilder.getLastAverageCpuUtilization(),
                stateBuilder.getLastAverageResponseTime(),
                activeVms.size());

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

    public double getCurrentTime() {
        return currentTime;
    }

    private AdvsacState observe(final double observationTime) {
        return stateBuilder.build(
                observationTime,
                activeVms,
                cloudlets,
                arrivalTimes);
    }

    /**
     * Returns the actual number of VMs added/removed. Scale-in only removes
     * idle VMs to avoid terminating requests that are still running.
     */
    private int applyScalingAction(final int requestedDelta) {
        if (requestedDelta > 0) {
            int additions = Math.min(
                    requestedDelta,
                    AdvsacConfig.MAX_VM_COUNT - activeVms.size());
            for (int i = 0; i < additions; i++) {
                Vm vm = createVm(nextVmId++);
                activeVms.add(vm);
                List<Vm> singleton = new ArrayList<>(1);
                singleton.add(vm);
                broker.createVmsAfter(singleton, AdvsacConfig.PROVISIONING_DELAY_SECONDS);
                totalScaleUpOperations++;
            }
            return additions;
        }

        if (requestedDelta < 0) {
            int removalsRequested = Math.min(
                    -requestedDelta,
                    activeVms.size() - AdvsacConfig.MIN_VM_COUNT);
            int removed = 0;

            for (int i = activeVms.size() - 1; i >= 0 && removed < removalsRequested; i--) {
                Vm candidate = activeVms.get(i);
                if (candidate.getTotalUtilizationOfCpu(currentTime) <= 0.001) {
                    List<Vm> singleton = new ArrayList<>(1);
                    singleton.add(candidate);
                    broker.destroyVMsAfter(singleton, 0.0);
                    activeVms.remove(i);
                    totalScaleDownOperations++;
                    removed++;
                }
            }
            return -removed;
        }

        return 0;
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
