package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Integration smoke test for the CloudSim-backed environment before RL training.
 *
 * It checks the 10-second decision interval, state normalization, finite rewards,
 * VM bounds, action-index handling, and termination at exactly 1000 simulated
 * seconds. Scale-in may remove fewer VMs than requested when the remaining VMs
 * are busy; the environment must never destroy a VM with unfinished work.
 */
public final class AdvsacEnvironmentSmokeTest {

    private static final int EXPECTED_STEPS = 100;
    private static final double TIME_TOLERANCE = 1.0e-6;

    private AdvsacEnvironmentSmokeTest() {
    }

    public static void main(final String[] args) {
        AdvsacCloudSimEnvironment environment = new AdvsacCloudSimEnvironment();
        var initialState = environment.reset();

        validateState(initialState.toArray(), 0);
        if (environment.getActiveVmCount() != AdvsacConfig.INITIAL_VM_COUNT) {
            throw new IllegalStateException("Reset must start with exactly "
                    + AdvsacConfig.INITIAL_VM_COUNT + " VMs.");
        }

        System.out.println("Initial observation time: "
                + environment.getCurrentTime() + " seconds");
        System.out.println("Initial state: " + initialState);
        System.out.println("Action mapping: 0=-2, 1=-1, 2=0, 3=+1, 4=+2");

        // Exercise all five actions repeatedly. Busy VMs may be drained later,
        // so this smoke test validates safety and bounds rather than assuming
        // every scale-in can immediately remove the full requested number.
        int[] testActions = {3, 4, 2, 1, 0};
        int actionPosition = 0;
        int steps = 0;

        while (steps < EXPECTED_STEPS) {
            int actionIndex = testActions[actionPosition];
            int actionValue = AdvsacConfig.actionValueFromIndex(actionIndex);

            var transition = environment.step(actionIndex);
            steps++;

            double expectedTime = steps * AdvsacConfig.RL_TIMESTEP;
            if (Math.abs(transition.getSimulationTime() - expectedTime)
                    > TIME_TOLERANCE) {
                throw new IllegalStateException("Expected simulation time "
                        + expectedTime + " but got "
                        + transition.getSimulationTime() + ".");
            }

            if (!Double.isFinite(transition.getReward())) {
                throw new IllegalStateException("Non-finite reward at step " + steps);
            }

            validateState(transition.getNextState().toArray(), steps);

            int vmCount = environment.getActiveVmCount();
            if (vmCount < AdvsacConfig.MIN_VM_COUNT
                    || vmCount > AdvsacConfig.MAX_VM_COUNT) {
                throw new IllegalStateException("VM count outside configured bounds at step "
                        + steps + ": " + vmCount);
            }

            if (transition.isDone() != (steps == EXPECTED_STEPS)) {
                throw new IllegalStateException(
                        "Episode termination flag is incorrect at step " + steps);
            }

            System.out.println("SMOKE | step=" + steps
                    + " | t=" + transition.getSimulationTime()
                    + " | actionIndex=" + actionIndex
                    + " | action=" + actionValue
                    + " | reward=" + transition.getReward()
                    + " | VMs=" + vmCount
                    + " | state=" + transition.getNextState());

            actionPosition = (actionPosition + 1) % testActions.length;
        }

        if (Math.abs(environment.getCurrentTime() - AdvsacConfig.EPISODE_DURATION)
                > TIME_TOLERANCE) {
            throw new IllegalStateException("Episode did not end at "
                    + AdvsacConfig.EPISODE_DURATION + " seconds.");
        }

        System.out.println("Total steps: " + steps);
        System.out.println("Scale-up operations: "
                + environment.getTotalScaleUpOperations());
        System.out.println("Scale-down operations: "
                + environment.getTotalScaleDownOperations());
        System.out.println("SMOKE TEST PASSED.");
    }

    private static void validateState(final double[] values, final int step) {
        if (values.length != AdvsacConfig.STATE_DIMENSION) {
            throw new IllegalStateException("Expected "
                    + AdvsacConfig.STATE_DIMENSION + " state values at step " + step);
        }

        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i]) || values[i] < 0.0 || values[i] > 1.0) {
                throw new IllegalStateException("Invalid normalized state value at step "
                        + step + ", index " + i + ": " + values[i]);
            }
        }
    }
}
