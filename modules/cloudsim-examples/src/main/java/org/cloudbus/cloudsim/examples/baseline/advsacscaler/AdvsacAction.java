package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Discrete action space used by Plain Discrete AdvSAC.
 *
 * The actor outputs a probability distribution over these five actions.
 */
public enum AdvsacAction {

    REMOVE_TWO(-2, 0),
    REMOVE_ONE(-1, 1),
    NO_CHANGE(0, 2),
    ADD_ONE(1, 3),
    ADD_TWO(2, 4);

    private final int vmDelta;
    private final int index;

    AdvsacAction(final int vmDelta, final int index) {
        this.vmDelta = vmDelta;
        this.index = index;
    }

    public int getVmDelta() {
        return vmDelta;
    }

    public int getIndex() {
        return index;
    }

    public static AdvsacAction fromIndex(final int index) {
        for (AdvsacAction action : values()) {
            if (action.index == index) {
                return action;
            }
        }

        throw new IllegalArgumentException(
                "Invalid AdvSAC action index: " + index);
    }

    public static AdvsacAction fromVmDelta(final int vmDelta) {
        for (AdvsacAction action : values()) {
            if (action.vmDelta == vmDelta) {
                return action;
            }
        }

        throw new IllegalArgumentException(
                "Invalid VM delta: " + vmDelta);
    }
}
