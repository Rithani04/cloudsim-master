package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Integration smoke test for the CloudSim-backed environment before RL training.
 *
 * It checks response-time and reward arithmetic, the 10-second decision interval,
 * state normalization, finite rewards, VM bounds, action-index handling, and
 * termination at exactly 1000 simulated seconds. Scale-in may remove fewer VMs
 * than requested when the remaining VMs are busy; the environment must never
 * destroy a VM with unfinished work.
 */
public final class AdvsacEnvironmentSmokeTest {

    private static final int EXPECTED_STEPS = 100;
    private static final double TIME_TOLERANCE = 1.0e-6;

    private AdvsacEnvironmentSmokeTest() {
    }

    public static void main(final String[] args) {
        validateResponseTimeAndRewardCalculations();

        AdvsacCloudSimEnvironment environment = new AdvsacCloudSimEnvironment();
        var initialState = environment.reset();

        validateState(initialState.toArray(), 0);
        double[] initialValues = initialState.toArray();
        if (initialValues[2] != 0.0) {
            throw new IllegalStateException(
                    "Initial workload must be zero before CloudSim starts; got "
                            + initialValues[2]);
        }
        if (initialValues[3] != 0.5) {
            throw new IllegalStateException(
                    "Initial workload growth must be neutral before observations exist; got "
                            + initialValues[3]);
        }
        if (initialValues[1] != 0.0) {
            throw new IllegalStateException(
                    "Initial memory utilization must be zero before CloudSim starts; got "
                            + initialValues[1]);
        }
        if (initialValues[4] != 0.0) {
            throw new IllegalStateException(
                    "Initial waiting queue must be zero before any cloudlet arrival; got "
                            + initialValues[4]);
        }
        if (initialValues[7] != 1.0) {
            throw new IllegalStateException(
                    "Initial SLA state must be 1 when no response-time violation has occurred.");
        }
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

            var unprovisionedVmIds = environment.getUnprovisionedActiveVmIds();
            if (!unprovisionedVmIds.isEmpty()) {
                throw new IllegalStateException(
                        "Active VMs are not fully provisioned at step " + steps
                                + " (t=" + transition.getSimulationTime() + "): "
                                + unprovisionedVmIds
                                + ". Each active VM must have a host and a successful "
                                + "CloudSim creation acknowledgement.");
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

    private static void validateResponseTimeAndRewardCalculations() {
        assertClose(
                3.5,
                AdvsacStateBuilder.calculateResponseTime(12.0, 8.5),
                "Response time must equal finish time minus arrival time");
        assertClose(
                -1.0,
                AdvsacStateBuilder.calculateResponseTime(8.0, 8.5),
                "Finish before arrival must be rejected");

        AdvsacRewardCalculator calculator = new AdvsacRewardCalculator();
        double cpu = 0.8;
        assertClose(0.8, calculator.calculate(cpu, 9.0, 3, false),
                "Latency below SLO should have no SLA penalty");
        assertClose(0.8, calculator.calculate(cpu, 10.0, 3, false),
                "Latency exactly at SLO should have no SLA penalty");
        assertClose(0.79, calculator.calculate(cpu, 10.1, 3, false),
                "SLA penalty should increase smoothly above SLO");
        assertClose(-0.2, calculator.calculate(cpu, 20.0, 3, false),
                "SLA penalty should cap at one");
        assertClose(-0.2, calculator.calculate(cpu, 100.0, 3, false),
                "Extreme latency should not create an unbounded penalty");
        assertClose(-0.2, calculator.calculate(cpu, 10.0, 3, true),
                "Constraint violation should apply the configured penalty");

        System.out.println("Response-time and reward checks passed.");
    }

    private static void assertClose(
            final double expected,
            final double actual,
            final String message) {
        if (Math.abs(expected - actual) > 1.0e-9) {
            throw new IllegalStateException(
                    message + ": expected " + expected + " but got " + actual);
        }
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
