package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Manual integration smoke test for the CloudSim environment before RL is added.
 *
 * Run this class as a Java application. It cycles through all five discrete
 * actions so the test checks reset/step timing, observations, reward
 * calculation, episode termination and VM scaling integration without
 * introducing a learning policy.
 */
public final class AdvsacEnvironmentSmokeTest {

    private AdvsacEnvironmentSmokeTest() {
    }

    public static void main(final String[] args) {
        AdvsacCloudSimEnvironment environment = new AdvsacCloudSimEnvironment();
        AdvsacState state = environment.reset();

        System.out.println("Workload model: " + AdvsacConfig.WORKLOAD_MODEL);
        System.out.println("Initial observation time: "
                + environment.getCurrentTime() + " seconds");
        System.out.println("Initial state: " + state);

        int[] testActions = {
            AdvsacAction.ADD_ONE.getIndex(),
            AdvsacAction.ADD_TWO.getIndex(),
            AdvsacAction.NO_CHANGE.getIndex(),
            AdvsacAction.REMOVE_ONE.getIndex(),
            AdvsacAction.REMOVE_TWO.getIndex()
        };

        int actionPosition = 0;
        int steps = 0;

        while (true) {
            int actionIndex = testActions[actionPosition];
            AdvsacTransition transition = environment.step(actionIndex);
            steps++;

            System.out.println(
                    "SMOKE | step=" + steps
                            + " | t=" + transition.getSimulationTime()
                            + " | actionIndex=" + actionIndex
                            + " | action=" + AdvsacConfig.actionValueFromIndex(actionIndex)
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
    }
}
