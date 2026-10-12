package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Configuration for the Plain Discrete AdvSAC implementation.
 *
 * Phase 2 scope:
 * - Plain Discrete AdvSAC only.
 * - No uncertainty estimation.
 * - No distribution-shift detection.
 * - No risk penalty.
 *
 * Parameters explicitly marked as TBD are intentionally not given
 * research values yet.
 */
public final class AdvsacConfig {

    /** RL decision interval in simulated seconds. */
    public static final double RL_TIMESTEP = 10.0;

    /** Number of elements in the RL state vector. */
    public static final int STATE_DIMENSION = 8;

    /** Number of discrete actions. */
    public static final int ACTION_DIMENSION = 5;

    /** State normalization lower bound. */
    public static final double NORMALIZATION_MIN = 0.0;

    /** State normalization upper bound. */
    public static final double NORMALIZATION_MAX = 1.0;

    /**
     * Fixed action values.
     *
     * Index mapping:
     * 0 -> remove 2 VMs
     * 1 -> remove 1 VM
     * 2 -> no change
     * 3 -> add 1 VM
     * 4 -> add 2 VMs
     */
    public static final int[] ACTION_VALUES = {-2, -1, 0, 1, 2};

    /** Number of workload models. */
    public static final int NUMBER_OF_WORKLOAD_MODELS = 4;

    /*
     * The following values remain intentionally unresolved.
     * They must be supplied before a full training experiment.
     */
    public static final int MIN_VM_COUNT_TBD = -1;
    public static final int INITIAL_VM_COUNT_TBD = -1;
    public static final int MAX_VM_COUNT_TBD = -1;

    public static final double EPISODE_DURATION_TBD = -1.0;
    public static final double SLO_TBD = -1.0;

    private AdvsacConfig() {
        // Utility class.
    }

    /**
     * Converts an action-index produced by the actor into the
     * corresponding VM scaling action.
     *
     * @param actionIndex index in [0, 4]
     * @return VM delta in [-2, 2]
     */
    public static int actionValueFromIndex(final int actionIndex) {
        if (actionIndex < 0 || actionIndex >= ACTION_DIMENSION) {
            throw new IllegalArgumentException(
                    "Invalid action index: " + actionIndex);
        }

        return ACTION_VALUES[actionIndex];
    }

    /**
     * Converts a VM scaling action into its actor output index.
     *
     * @param actionValue one of {-2, -1, 0, +1, +2}
     * @return action index in [0, 4]
     */
    public static int actionIndexFromValue(final int actionValue) {
        if (actionValue < -2 || actionValue > 2) {
            throw new IllegalArgumentException(
                    "Invalid action value: " + actionValue);
        }

        return actionValue + 2;
    }
}
