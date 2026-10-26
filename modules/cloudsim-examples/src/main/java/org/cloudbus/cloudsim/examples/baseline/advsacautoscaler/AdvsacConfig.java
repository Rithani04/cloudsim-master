package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Configuration for the ASTRA-inspired discrete AdvSAC experiment.
 *
 * These are project experiment settings, not values copied wholesale
 * from the ASTRA paper. Uncertainty, distribution-shift detection,
 * adversarial perturbations and risk penalties are out of scope here.
 */
public final class AdvsacConfig {

    public static final double RL_TIMESTEP = 10.0;
    public static final int STATE_DIMENSION = 8;
    public static final int ACTION_DIMENSION = 5;
    public static final double NORMALIZATION_MIN = 0.0;
    public static final double NORMALIZATION_MAX = 1.0;

    /** Actor output index: 0 remove 2, 1 remove 1, 2 no-op, 3 add 1, 4 add 2. */
    public static final int[] ACTION_VALUES = {-2, -1, 0, 1, 2};

    public static final int NUMBER_OF_WORKLOAD_MODELS = 4;

    public static final int MIN_VM_COUNT = 1;
    public static final int INITIAL_VM_COUNT = 3;
    public static final int MAX_VM_COUNT = 12;

    public static final double EPISODE_DURATION = 1000.0;
    public static final double SLO_SECONDS = 10.0;
    public static final double CPU_UTILIZATION_TARGET = 0.70;
    public static final double MEMORY_UTILIZATION_TARGET = 0.70;
    public static final double PROVISIONING_DELAY_SECONDS = 0.0;
    public static final double SCALE_COOLDOWN_SECONDS = 0.0;
    public static final double INSTANCE_CONSTRAINT_PENALTY = -1.0;
    public static final long RANDOM_SEED = 42L;

    /* Initial training hyperparameters; validate through controlled experiments. */
    public static final int ACTOR_HIDDEN_SIZE = 256;
    public static final int CRITIC_HIDDEN_SIZE = 256;
    public static final int NETWORK_DEPTH = 3;
    public static final double ACTOR_LEARNING_RATE = 1.0e-4;
    public static final double CRITIC_LEARNING_RATE = 1.0e-4;
    public static final double DISCOUNT_FACTOR = 0.99;
    public static final double TARGET_UPDATE_TAU = 0.01;

    /** Bounded L-infinity state perturbation strength for ASTRA-inspired AdvSAC. */
    public static final double ADVERSARIAL_EPSILON = 0.02;
    public static final int REPLAY_BUFFER_CAPACITY = 100_000;
    public static final int BATCH_SIZE = 128;
    public static final int TRAINING_EPISODES = 200;

    /**
     * Select with -Dadvsac.workload=STABLE|PERIODIC|GRADUAL_INCREASE|SUDDEN_BURST.
     * STABLE is used if the property is missing; invalid values fail fast.
     */
    public enum WorkloadModel {
        STABLE,
        PERIODIC,
        GRADUAL_INCREASE,
        SUDDEN_BURST
    }

    public static final WorkloadModel WORKLOAD_MODEL = readWorkloadModel();

    private AdvsacConfig() {
        // Utility class.
    }

    private static WorkloadModel readWorkloadModel() {
        String configured = System.getProperty("advsac.workload", "STABLE");
        try {
            return WorkloadModel.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown advsac.workload '" + configured
                            + "'. Choose STABLE, PERIODIC, GRADUAL_INCREASE or SUDDEN_BURST.",
                    exception);
        }
    }

    public static int actionValueFromIndex(final int actionIndex) {
        if (actionIndex < 0 || actionIndex >= ACTION_DIMENSION) {
            throw new IllegalArgumentException("Invalid action index: " + actionIndex);
        }
        return ACTION_VALUES[actionIndex];
    }

    public static int actionIndexFromValue(final int actionValue) {
        if (actionValue < -2 || actionValue > 2) {
            throw new IllegalArgumentException("Invalid action value: " + actionValue);
        }
        return actionValue + 2;
    }
}
