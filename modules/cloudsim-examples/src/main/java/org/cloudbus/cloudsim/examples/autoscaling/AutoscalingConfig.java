package org.cloudbus.cloudsim.examples.autoscaling;

/**
 * Configuration parameters for the CloudSim autoscaling research environment.
 *
 * Stage 1:
 * - Multi-host cloud infrastructure
 * - Multiple VM/application instances
 * - Time-varying workload arrivals
 *
 * Autoscaling decisions, PPO, uncertainty and distribution-shift
 * detection will be added in later stages.
 */
public class AutoscalingConfig {

    /* =========================================================
       CLOUD INFRASTRUCTURE
       ========================================================= */

    // Number of physical hosts in the datacenter
    public static final int NUMBER_OF_HOSTS = 4;

    // CPU capacity of each host
    public static final int HOST_MIPS = 4000;

    // Number of CPU cores per host
    public static final int HOST_PES = 4;

    // RAM of each host in MB
    public static final int HOST_RAM = 16384;

    // Bandwidth of each host
    public static final long HOST_BW = 10000;

    // Storage of each host in MB
    public static final long HOST_STORAGE = 1000000;


    /* =========================================================
       APPLICATION INSTANCE / VM CONFIGURATION
       ========================================================= */

    // One VM represents one application instance / replica
    public static final int MIN_INSTANCES = 1;

    public static final int INITIAL_INSTANCES = 3;

    public static final int MAX_INSTANCES = 12;

    // CPU capacity of one application instance
    public static final int VM_MIPS = 1000;

    // Number of CPU cores per VM
    public static final int VM_PES = 1;

    // Memory allocated to one VM
    public static final int VM_RAM = 1024;

    // Bandwidth allocated to one VM
    public static final long VM_BW = 1000;

    // VM disk size
    public static final long VM_SIZE = 10000;


    /* =========================================================
       WORKLOAD CONFIGURATION
       ========================================================= */

    // Number of cloudlets/requests generated in Stage 1
    public static final int NUMBER_OF_CLOUDLETS = 100;

    // Length of each cloudlet in MI
    public static final long CLOUDLET_LENGTH = 10000;

    // Cloudlet input size
    public static final long CLOUDLET_FILE_SIZE = 300;

    // Cloudlet output size
    public static final long CLOUDLET_OUTPUT_SIZE = 300;


    /* =========================================================
       SIMULATION CONFIGURATION
       ========================================================= */

    // Simulation duration in seconds
    public static final double SIMULATION_TIME = 1000.0;

    // Future autoscaling decision interval
    // We are not using it for scaling yet.
    public static final double DECISION_INTERVAL = 10.0;


    /* =========================================================
       COST PARAMETERS
       ========================================================= */

    public static final double COST_PER_CPU = 3.0;

    public static final double COST_PER_MEMORY = 0.05;

    public static final double COST_PER_STORAGE = 0.001;

    public static final double COST_PER_BW = 0.0;


    /* =========================================================
       WORKLOAD REGIMES
       ========================================================= */

    public enum WorkloadRegime {
        NORMAL,
        PERIODIC,
        GRADUAL_INCREASE,
        SUDDEN_BURST
    }


    // Stage 1 workload regime
    public static final WorkloadRegime WORKLOAD_REGIME =
            WorkloadRegime.NORMAL;


    private AutoscalingConfig() {
        // Prevent object creation.
    }
}