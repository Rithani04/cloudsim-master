package org.cloudbus.cloudsim.examples.autoscaling.broker;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.EX.DatacenterBrokerEX;
import org.cloudbus.cloudsim.core.GuestEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Broker that dynamically assigns each cloudlet to one of the VMs
 * that are currently active.
 *
 * Cloudlets are assigned to a VM when their arrival event occurs.
 * They are not permanently assigned when the workload is generated.
 */
public class DynamicCloudletBroker extends DatacenterBrokerEX {

    /**
     * Number of cloudlets whose arrival events have been scheduled
     * but which have not necessarily arrived yet.
     *
     * This is important because the parent DatacenterBroker only
     * knows about cloudlets after their delayed arrival event fires.
     */
    private int scheduledCloudletCount = 0;

    /**
     * Creates the broker.
     *
     * @param name broker name
     * @throws Exception if broker creation fails
     */
    public DynamicCloudletBroker(String name) throws Exception {
        super(name);
    }

    /**
     * Creates the broker with a specified lifetime.
     *
     * @param name broker name
     * @param lifeLength broker lifetime
     * @throws Exception if broker creation fails
     */
    public DynamicCloudletBroker(String name, double lifeLength)
            throws Exception {
        super(name, lifeLength);
    }

    /**
     * Registers that cloudlets have been scheduled for future arrival.
     *
     * Call this from the workload-generation code before scheduling
     * each cloudlet.
     */
    public void registerScheduledCloudlets(int count) {
        scheduledCloudletCount += count;
    }

    /**
     * Submit waiting cloudlets to currently active VMs.
     *
     * Unlike the default DatacenterBroker implementation, this method
     * does not use the parent's persistent guestIndex.
     *
     * Instead, for every cloudlet that has actually arrived:
     *
     *      cloudlet arrival
     *              ↓
     *      current active VMs
     *              ↓
     *      select VM
     *              ↓
     *      submit cloudlet
     */
    @Override
    protected void processCloudletReturn(
            org.cloudbus.cloudsim.core.SimEvent ev) {

        Cloudlet cloudlet = (Cloudlet) ev.getData();

        getCloudletReceivedList().add(cloudlet);

        Log.printlnConcat(
                CloudSim.clock(),
                ": ",
                getName(),
                ": ",
                cloudlet.getClass().getSimpleName(),
                " #",
                cloudlet.getCloudletId(),
                " return received"
        );

        cloudletsSubmitted--;

        /*
         * Only finish when EVERY scheduled cloudlet has actually
         * returned.
         */
        if (getCloudletReceivedList().size()
                >= scheduledCloudletCount
                && cloudletsSubmitted == 0) {

            Log.printlnConcat(
                    CloudSim.clock(),
                    ": ",
                    getName(),
                    ": All scheduled Cloudlets executed. Finishing..."
            );

            clearDatacenters();
            finishExecution();
        }
    }
    @Override
    protected void submitCloudlets() {

        List<GuestEntity> activeGuests =
                new ArrayList<>(getGuestsCreatedList());

        if (activeGuests.isEmpty()) {
            Log.printlnConcat(
                    CloudSim.clock(),
                    ": ",
                    getName(),
                    ": No active VMs available. Cloudlets remain queued."
            );
            return;
        }

        List<Cloudlet> successfullySubmitted =
                new ArrayList<>();

        for (Cloudlet cloudlet : getCloudletList()) {

            Vm selectedVm = selectVm(activeGuests);

            if (selectedVm == null) {
                continue;
            }

            Integer datacenterId =
                    getVmsToDatacentersMap().get(selectedVm.getId());

            if (datacenterId == null) {
                continue;
            }

            /*
             * Bind this cloudlet to the VM selected RIGHT NOW.
             */
            cloudlet.setGuestId(selectedVm.getId());

            Log.printlnConcat(
                    CloudSim.clock(),
                    ": ",
                    getName(),
                    ": Assigning Cloudlet #",
                    cloudlet.getCloudletId(),
                    " to VM #",
                    selectedVm.getId()
            );

            /*
             * Send the cloudlet to the selected VM.
             */
            sendNow(
                    datacenterId,
                    CloudActionTags.CLOUDLET_SUBMIT,
                    cloudlet
            );

            /*
             * Maintain the same bookkeeping used by the parent broker.
             */
            cloudletsSubmitted++;

            getCloudletSubmittedList().add(cloudlet);

            successfullySubmitted.add(cloudlet);
        }

        /*
         * Remove cloudlets that were actually submitted.
         */
        getCloudletList().removeAll(successfullySubmitted);
    }

    /**
     * Select the least CPU-utilized currently active VM.
     */
    private Vm selectVm(List<GuestEntity> activeGuests) {

        Vm selectedVm = null;

        double lowestUtilization = Double.MAX_VALUE;

        double currentTime = CloudSim.clock();

        for (GuestEntity guest : activeGuests) {

            if (!(guest instanceof Vm vm)) {
                continue;
            }

            double utilization =
                    vm.getTotalUtilizationOfCpu(currentTime);

            if (Double.isNaN(utilization)) {
                utilization = 0.0;
            }

            if (utilization < lowestUtilization) {
                lowestUtilization = utilization;
                selectedVm = vm;
            }
        }

        return selectedVm;
    }

    /**
     * Returns the number of cloudlets that have been scheduled
     * for future arrival.
     */
    public int getScheduledCloudletCount() {
        return scheduledCloudletCount;
    }

    /**
     * Returns the number of cloudlets that have already arrived,
     * been submitted, or completed.
     *
     * This is useful for debugging the broker lifecycle.
     */
    public int getArrivedCloudletCount() {
        return getCloudletReceivedList().size()
                + getCloudletSubmittedList().size()
                + getCloudletList().size();
    }

    /**
     * Returns whether the broker still has work that can arrive
     * or is currently being processed.
     */
    public boolean hasPendingWork() {
        return scheduledCloudletCount > getCloudletReceivedList().size();
    }
}