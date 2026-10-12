package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * One transition produced by the RL environment.
 */
public final class AdvsacTransition {

    private final AdvsacState nextState;
    private final double reward;
    private final boolean done;
    private final int actionIndex;
    private final double simulationTime;

    public AdvsacTransition(
            final AdvsacState nextState,
            final double reward,
            final boolean done,
            final int actionIndex,
            final double simulationTime) {

        if (nextState == null) {
            throw new IllegalArgumentException("nextState cannot be null.");
        }

        this.nextState = nextState;
        this.reward = reward;
        this.done = done;
        this.actionIndex = actionIndex;
        this.simulationTime = simulationTime;
    }

    public AdvsacState getNextState() {
        return nextState;
    }

    public double getReward() {
        return reward;
    }

    public boolean isDone() {
        return done;
    }

    public int getActionIndex() {
        return actionIndex;
    }

    public double getSimulationTime() {
        return simulationTime;
    }
}
