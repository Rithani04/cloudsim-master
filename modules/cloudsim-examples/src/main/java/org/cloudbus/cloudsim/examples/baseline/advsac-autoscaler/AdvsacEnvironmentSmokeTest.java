package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Manual integration smoke test for the CloudSim environment before RL is added.
 *
 * Run this class as a Java application. It uses NO_CHANGE for each action so
 * the test checks reset/step timing, observations, reward calculation and
 * episode termination without introducing a learning policy.
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

        int noChangeActionIndex = AdvsacAction.NO_CHANGE.getIndex();
        int steps = 0;

        while (true) {
            AdvsacTransition transition = environment.step(noChangeActionIndex);
            steps++;

            if (steps % 10 == 0 || transition.isDone()) {
                System.out.println(
                        "SMOKE | step=" + steps
                                + " | t=" + transition.getSimulationTime()
                                + " | reward=" + transition.getReward()
                                + " | VMs=" + environment.getActiveVmCount()
                                + " | state=" + transition.getNextState());
            }

            if (transition.isDone()) {
                break;
            }
        }

        System.out.println("Smoke test completed.");
        System.out.println("Scale-up operations: "
                + environment.getTotalScaleUpOperations());
        System.out.println("Scale-down operations: "
                + environment.getTotalScaleDownOperations());
    }
}
