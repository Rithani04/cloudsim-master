package org.cloudbus.cloudsim.examples.autoscaling;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.examples.autoscaling.broker.DynamicCloudletBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;

import org.cloudbus.cloudsim.core.CloudSim;

import org.cloudbus.cloudsim.distributions.ExponentialDistr;

import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.examples.autoscaling.state.CloudState;
import org.cloudbus.cloudsim.examples.autoscaling.state.CloudStateCollector;
import org.cloudbus.cloudsim.examples.autoscaling.controller.ThresholdAutoscaler;

/**
 * Stage 1 CloudSim environment for the
 * Distribution-Shift-Aware Safe Reinforcement Learning
 * for Cloud Autoscaling project.
 *
 * Current stage:
 *
 *     Workload
 *        ↓
 *     Cloudlets
 *        ↓
 *     VM/Application Instances
 *        ↓
 *     CloudSim Datacenter
 *        ↓
 *     Cloud State Collector
 *
 * No RL, PPO, uncertainty estimation or distribution-shift
 * detection is implemented yet.
 */
public class CloudAutoscalingExample {

    private static List<Vm> vmList;

    /*
     * Person 1:
     * Cloud state observation/collection.
     */
    private static List<Cloudlet> allCloudlets;
    private static CloudStateCollector stateCollector;


    public static void main(String[] args) throws Exception {
        Log.println("==============================================");
        Log.println(" Cloud Autoscaling CloudSim - Stage 1");
        Log.println("==============================================");

        try {

            /* =====================================================
               1. INITIALIZE CLOUDSIM
               ===================================================== */

            int numberOfUsers = 1;

            Calendar calendar = Calendar.getInstance();

            boolean traceFlag = false;

            CloudSim.init(
                    numberOfUsers,
                    calendar,
                    traceFlag
            );

            /*
             * Initialize the cloud state collector.
             */
            stateCollector =
                    new CloudStateCollector();
            allCloudlets =
                    new ArrayList<>();


            /* =====================================================
               2. CREATE DATACENTER
               ===================================================== */

            Datacenter datacenter =
                    createDatacenter(
                            "Autoscaling_Datacenter"
                    );


            /* =====================================================
               3. CREATE BROKER
               ===================================================== */

            DynamicCloudletBroker broker =
                    createBroker();

            int brokerId =
                    broker.getId();

            ThresholdAutoscaler thresholdAutoscaler =
                    new ThresholdAutoscaler();


            /* =====================================================
               4. CREATE APPLICATION INSTANCES
               ===================================================== */

            vmList =
                    createVmList(
                            brokerId
                    );

            broker.submitGuestList(
                    vmList
            );

            int datacenterId =
                    datacenter.getId();

            AutoscalingManager autoscalingManager =
                    new AutoscalingManager(

                            "Autoscaling_Manager",

                            broker,

                            thresholdAutoscaler,

                            stateCollector,

                            vmList,

                            allCloudlets,

                            datacenterId
                    );


            Log.println();

            Log.println(
                    "Infrastructure created:"
            );

            Log.println(
                    "Hosts              : "
                            + AutoscalingConfig.NUMBER_OF_HOSTS
            );

            Log.println(
                    "Maximum instances  : "
                            + AutoscalingConfig.MAX_INSTANCES
            );

            Log.println(
                    "Initial instances  : "
                            + AutoscalingConfig.INITIAL_INSTANCES
            );


            /* =====================================================
               5. CREATE WORKLOAD
               ===================================================== */

            createWorkload(
                    broker,
                    brokerId
            );


            /* =====================================================
               6. SET SIMULATION TERMINATION TIME
               ===================================================== */

            CloudSim.terminateSimulation(
                    AutoscalingConfig.SIMULATION_TIME
            );


            /* =====================================================
               7. START SIMULATION
               ===================================================== */

            Log.println();

            Log.println(
                    "Starting CloudSim simulation..."
            );

            CloudSim.startSimulation();

            CloudSim.stopSimulation();


            /* =====================================================
               8. COLLECT COMPLETED CLOUDLETS
               ===================================================== */

            List<Cloudlet> completedCloudlets =
                    broker.getCloudletReceivedList();


            /* =====================================================
               9. COLLECT FINAL CLOUD STATE
               ===================================================== */

            CloudState finalState =
                    stateCollector.collectState(

                            AutoscalingConfig.SIMULATION_TIME,

                            /*
                             * Current Stage 1 workload metric.
                             *
                             * This is currently the total number
                             * of generated cloudlets.
                             *
                             * Later this can be replaced by
                             * instantaneous/request-rate workload.
                             */
                            AutoscalingConfig.NUMBER_OF_CLOUDLETS,

                            calculateAverageCpuUtilization(),

                            countActiveVms(),

                            completedCloudlets
                    );


            Log.println();

            Log.println(
                    "========== FINAL CLOUD STATE =========="
            );

            Log.println(
                    finalState
            );

            Log.println(
                    "======================================="
            );


            /* =====================================================
               10. PRINT SIMULATION SUMMARY
               ===================================================== */

            printSimulationSummary(
                    completedCloudlets
            );


            /* =====================================================
               11. PRINT CLOUDLET RESULTS
               ===================================================== */

            printCloudletList(
                    completedCloudlets
            );


            Log.println();

            Log.println(
                    "Cloud autoscaling Stage 1 finished!"
            );


        } catch (Exception e) {

            e.printStackTrace();

            Log.println(
                    "An error occurred during the simulation."
            );
        }
    }


    /* =============================================================
       CREATE DATACENTER
       ============================================================= */

    private static Datacenter createDatacenter(
            String name) {

        List<Host> hostList =
                new ArrayList<>();


        for (
                int hostId = 0;
                hostId < AutoscalingConfig.NUMBER_OF_HOSTS;
                hostId++
        ) {


            /* -----------------------------------------------------
               CREATE CPU CORES
               ----------------------------------------------------- */

            List<Pe> peList =
                    new ArrayList<>();


            for (
                    int peId = 0;
                    peId < AutoscalingConfig.HOST_PES;
                    peId++
            ) {

                peList.add(
                        new Pe(
                                peId,
                                new PeProvisionerSimple(
                                        AutoscalingConfig.HOST_MIPS
                                )
                        )
                );
            }


            /* -----------------------------------------------------
               CREATE HOST
               ----------------------------------------------------- */

            Host host =
                    new Host(

                            hostId,

                            new RamProvisionerSimple(
                                    AutoscalingConfig.HOST_RAM
                            ),

                            new BwProvisionerSimple(
                                    AutoscalingConfig.HOST_BW
                            ),

                            AutoscalingConfig.HOST_STORAGE,

                            peList,

                            new VmSchedulerTimeShared(
                                    peList
                            )
                    );


            hostList.add(
                    host
            );
        }


        /* =========================================================
           DATACENTER CHARACTERISTICS
           ========================================================= */

        String architecture =
                "x86";

        String operatingSystem =
                "Linux";

        String virtualMachineMonitor =
                "Xen";

        double timeZone =
                5.5;

        double costPerCpu =
                AutoscalingConfig.COST_PER_CPU;

        double costPerMemory =
                AutoscalingConfig.COST_PER_MEMORY;

        double costPerStorage =
                AutoscalingConfig.COST_PER_STORAGE;

        double costPerBandwidth =
                AutoscalingConfig.COST_PER_BW;


        LinkedList<Storage> storageList =
                new LinkedList<>();


        DatacenterCharacteristics characteristics =
                new DatacenterCharacteristics(

                        architecture,

                        operatingSystem,

                        virtualMachineMonitor,

                        hostList,

                        timeZone,

                        costPerCpu,

                        costPerMemory,

                        costPerStorage,

                        costPerBandwidth
                );


        /* =========================================================
           CREATE DATACENTER
           ========================================================= */

        Datacenter datacenter =
                null;


        try {

            datacenter =
                    new Datacenter(

                            name,

                            characteristics,

                            new VmAllocationPolicySimple(
                                    hostList
                            ),

                            storageList,

                            0
                    );

        } catch (Exception e) {

            e.printStackTrace();
        }


        return datacenter;
    }


    /* =============================================================
       CREATE BROKER
       ============================================================= */

    private static DynamicCloudletBroker createBroker() {

        DynamicCloudletBroker broker;

        try {

            broker =
                    new DynamicCloudletBroker(
                            "Broker"
                    );

        } catch (Exception e) {

            throw new RuntimeException(e);
        }

        return broker;
    }


    /* =============================================================
       CREATE VM / APPLICATION INSTANCES
       ============================================================= */

    private static List<Vm> createVmList(
            int brokerId) {


        List<Vm> vmList =
                new ArrayList<>();


        /*
         * Stage 1:
         *
         * We create the INITIAL_INSTANCES number
         * of VMs at the beginning of the simulation.
         *
         * IMPORTANT:
         *
         * MAX_INSTANCES is only the maximum allowed
         * number of instances.
         *
         * Person 3 will later implement the VM lifecycle
         * and dynamic creation/removal of instances.
         */


        for (
                int vmId = 0;
                vmId < AutoscalingConfig.INITIAL_INSTANCES;
                vmId++
        ) {


            Vm vm =
                    new Vm(

                            vmId,

                            brokerId,

                            AutoscalingConfig.VM_MIPS,

                            AutoscalingConfig.VM_PES,

                            AutoscalingConfig.VM_RAM,

                            AutoscalingConfig.VM_BW,

                            AutoscalingConfig.VM_SIZE,

                            "Xen",

                            new CloudletSchedulerTimeShared()
                    );


            vmList.add(
                    vm
            );
        }


        return vmList;
    }


    /* =============================================================
       CREATE WORKLOAD
       ============================================================= */

    private static void createWorkload(
            DynamicCloudletBroker broker,
            int brokerId) {


        UtilizationModel utilizationModel =
                new UtilizationModelFull();


        /*
         * Exponential distribution is taken from the
         * existing RealisticCloudletArrivalExample
         * in this CloudSim installation.
         */

        ExponentialDistr exponentialDistribution =
                new ExponentialDistr(
                        5,
                        1000
                );


        for (
                int i = 0;
                i < AutoscalingConfig.NUMBER_OF_CLOUDLETS;
                i++
        ) {


            long cloudletLength =
                    getCloudletLength(
                            i
                    );


            Cloudlet cloudlet =
                    new Cloudlet(

                            i,

                            cloudletLength,

                            AutoscalingConfig.VM_PES,

                            AutoscalingConfig.CLOUDLET_FILE_SIZE,

                            AutoscalingConfig.CLOUDLET_OUTPUT_SIZE,

                            utilizationModel,

                            utilizationModel,

                            utilizationModel
                    );


            cloudlet.setUserId(
                    brokerId
            );

            allCloudlets.add(
                    cloudlet
            );


            /*
             * Submit the cloudlet at a
             * workload-dependent arrival time.
             */

            double arrivalTime =
                    getArrivalTime(
                            i,
                            exponentialDistribution
                    );


            List<Cloudlet> cloudletList =
                    new ArrayList<>();


            cloudletList.add(
                    cloudlet
            );


            broker.submitCloudletList(
                    cloudletList,
                    arrivalTime
            );
            broker.registerScheduledCloudlets(1);
        }
    }


    /* =============================================================
       WORKLOAD LENGTH
       ============================================================= */

    private static long getCloudletLength(
            int cloudletId) {


        switch (
                AutoscalingConfig.WORKLOAD_REGIME
        ) {


            case NORMAL:

                return AutoscalingConfig.CLOUDLET_LENGTH;


            case PERIODIC:

                if (
                        (cloudletId / 10) % 2 == 0
                ) {

                    return AutoscalingConfig.CLOUDLET_LENGTH;

                } else {

                    return AutoscalingConfig.CLOUDLET_LENGTH * 2;
                }


            case GRADUAL_INCREASE:

                return AutoscalingConfig.CLOUDLET_LENGTH
                        + (cloudletId * 200);


            case SUDDEN_BURST:

                /*
                 * First part is normal.
                 * Later cloudlets represent
                 * a sudden increase in workload.
                 */

                if (cloudletId < 70) {

                    return AutoscalingConfig.CLOUDLET_LENGTH;

                } else {

                    return AutoscalingConfig.CLOUDLET_LENGTH * 4;
                }


            default:

                return AutoscalingConfig.CLOUDLET_LENGTH;
        }
    }


    /* =============================================================
       WORKLOAD ARRIVAL TIME
       ============================================================= */

    private static double getArrivalTime(
            int cloudletId,
            ExponentialDistr distribution) {


        switch (
                AutoscalingConfig.WORKLOAD_REGIME
        ) {


            case NORMAL:

                /*
                 * Random arrival process.
                 */

                return distribution.sample();


            case PERIODIC:

                /*
                 * Periodic request arrivals.
                 */

                return 5.0 * cloudletId;


            case GRADUAL_INCREASE:

                /*
                 * Requests become more frequent
                 * over time.
                 */

                return 10.0
                        + (cloudletId * 2.0);


            case SUDDEN_BURST:

                /*
                 * First 70 requests arrive normally.
                 * Remaining requests arrive rapidly.
                 */

                if (cloudletId < 70) {

                    return 10.0 * cloudletId;

                } else {

                    return 700.0
                            + (cloudletId - 70) * 0.5;
                }


            default:

                return distribution.sample();
        }
    }


    /* =============================================================
       CALCULATE AVERAGE CPU UTILIZATION
       ============================================================= */

    private static double calculateAverageCpuUtilization() {

        if (
                vmList == null
                        || vmList.isEmpty()
        ) {

            return 0.0;
        }


        double totalUtilization =
                0.0;

        int count =
                0;


        /*
         * CloudSim.clock() gives the current
         * simulation time after the simulation ends.
         */

        double currentTime =
                CloudSim.clock();


        for (
                Vm vm :
                vmList
        ) {


            if (vm != null) {

                double utilization =
                        vm.getTotalUtilizationOfCpu(
                                currentTime
                        );


                totalUtilization +=
                        utilization;

                count++;
            }
        }


        if (count == 0) {

            return 0.0;
        }


        /*
         * VM CPU utilization is represented
         * as a value between 0 and 1.
         *
         * CloudState expects the value
         * to be displayed as a percentage.
         */

        return (
                totalUtilization / count
        ) * 100.0;
    }


    /* =============================================================
       COUNT ACTIVE VMS
       ============================================================= */

    private static int countActiveVms() {

        if (
                vmList == null
        ) {

            return 0;
        }


        int activeVms =
                0;


        for (
                Vm vm :
                vmList
        ) {


            if (vm != null) {

                activeVms++;
            }
        }


        return activeVms;
    }


    /* =============================================================
       PRINT SIMULATION SUMMARY
       ============================================================= */

    private static void printSimulationSummary(
            List<Cloudlet> cloudletList) {


        int successfulCloudlets =
                0;

        double totalResponseTime =
                0.0;


        for (
                Cloudlet cloudlet :
                cloudletList
        ) {


            if (
                    cloudlet.getStatus()
                            == Cloudlet.CloudletStatus.SUCCESS
            ) {


                successfulCloudlets++;


                double responseTime =
                        cloudlet.getExecFinishTime()
                                - cloudlet.getSubmissionTime();


                totalResponseTime +=
                        responseTime;
            }
        }


        double averageResponseTime =
                0.0;


        if (
                successfulCloudlets > 0
        ) {

            averageResponseTime =
                    totalResponseTime
                            / successfulCloudlets;
        }


        Log.println();

        Log.println(
                "========== SIMULATION SUMMARY =========="
        );


        Log.println(
                "Total Cloudlets       : "
                        + cloudletList.size()
        );


        Log.println(
                "Successful Cloudlets  : "
                        + successfulCloudlets
        );


        Log.println(
                "Initial Instances     : "
                        + AutoscalingConfig.INITIAL_INSTANCES
        );


        Log.println(
                "Maximum Instances     : "
                        + AutoscalingConfig.MAX_INSTANCES
        );


        Log.println(
                "Workload Regime       : "
                        + AutoscalingConfig.WORKLOAD_REGIME
        );


        Log.println(
                "Average Response Time : "
                        + new DecimalFormat("###.##")
                        .format(
                                averageResponseTime
                        )
        );


        Log.println(
                "========================================="
        );
    }


    /* =============================================================
       PRINT CLOUDLET RESULTS
       ============================================================= */

    private static void printCloudletList(
            List<Cloudlet> list) {


        String indent =
                "    ";


        Log.println();

        Log.println(
                "========== CLOUDLET RESULTS =========="
        );


        Log.println(
                "Cloudlet ID"
                        + indent
                        + "STATUS"
                        + indent
                        + "VM ID"
                        + indent
                        + "CPU Time"
                        + indent
                        + "Start"
                        + indent
                        + "Finish"
        );


        DecimalFormat dft =
                new DecimalFormat("###.##");


        for (
                Cloudlet cloudlet :
                list
        ) {


            if (
                    cloudlet.getStatus()
                            == Cloudlet.CloudletStatus.SUCCESS
            ) {


                Log.println(

                        indent
                                + cloudlet.getCloudletId()

                                + indent

                                + "SUCCESS"

                                + indent

                                + cloudlet.getGuestId()

                                + indent

                                + dft.format(
                                cloudlet.getActualCPUTime()
                        )

                                + indent

                                + dft.format(
                                cloudlet.getExecStartTime()
                        )

                                + indent

                                + dft.format(
                                cloudlet.getExecFinishTime()
                        )
                );
            }
        }
    }
}