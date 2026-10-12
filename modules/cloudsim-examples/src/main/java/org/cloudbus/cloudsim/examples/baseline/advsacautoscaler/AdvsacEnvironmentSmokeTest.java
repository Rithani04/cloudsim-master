package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Manual integration smoke test for the CloudSim environment before RL is added.
 *
 * The test uses the five discrete action indices directly:
 * 0 = remove 2, 1 = remove 1, 2 = no change, 3 = add 1, 4 = add 2.
 *
 * This keeps the smoke test independent of the AdvSAC configuration/action
 * classes. It validates the CloudSim environment, observations, rewards,
 * episode timing, VM scaling and episode termination.
 */
public final class AdvsacEnvironmentSmokeTest {

    private AdvsacEnvironmentSmokeTest() {
    }

    public static void main(final String[] args) {
        AdvsacCloudSimEnvironment environment = new AdvsacCloudSimEnvironment();
        var initialState = environment.reset();

        System.out.println("Initial observation time: "
                + environment.getCurrentTime() + " seconds");
        System.out.println("Initial state: " + initialState);
        System.out.println("Action mapping: 0=-2, 1=-1, 2=0, 3=+1, 4=+2");

        /*
         * Action indices:
         * 0 -> remove 2 VMs
         * 1 -> remove 1 VM
         * 2 -> no change
         * 3 -> add 1 VM
         * 4 -> add 2 VMs
         *
         * Start at 3 VMs:
         * +1 -> 4
         * +2 -> 6
         *  0 -> 6
         * -1 -> 5
         * -2 -> 3
         *
         * The cycle is then repeated for the whole 1000-second episode.
         */
        int[] testActions = {3, 4, 2, 1, 0};
        int actionPosition = 0;
        int steps = 0;

        while (true) {
            int actionIndex = testActions[actionPosition];
            int actionValue = actionIndex - 2;

            var transition = environment.step(actionIndex);
            steps++;

            System.out.println(
                    "SMOKE | step=" + steps
                            + " | t=" + transition.getSimulationTime()
                            + " | actionIndex=" + actionIndex
                            + " | action=" + actionValue
                            + " | reward=" + transition.getReward()
                            + " | VMs=" + environment.getActiveVmCount()
                            + " | state=" + transition.getNextState());

            actionPosition = (actionPosition + 1) % testActions.length;

            if (transition.isDone()) {
                break;
            }
        }

        System.out.println("Smoke test completed.");
        System.out.println("Total steps: " + steps);
        System.out.println("Scale-up operations: "
                + environment.getTotalScaleUpOperations());
        System.out.println("Scale-down operations: "
                + environment.getTotalScaleDownOperations());

        if (steps != 100) {
            throw new IllegalStateException(
                    "Expected 100 environment steps, but got " + steps);
        }

        System.out.println("SCALING VALIDATION PASSED.");
    }
}
