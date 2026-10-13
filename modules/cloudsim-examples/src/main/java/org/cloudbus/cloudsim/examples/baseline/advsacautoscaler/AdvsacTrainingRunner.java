package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Command-line entry point for training the plain discrete SAC baseline in
 * CloudSim. Select the workload with -Dadvsac.workload=STABLE|PERIODIC|
 * GRADUAL_INCREASE|SUDDEN_BURST.
 */
public final class AdvsacTrainingRunner {
    private AdvsacTrainingRunner() { }

    public static void main(final String[] args) {
        AdvsacAgent agent = new AdvsacAgent();
        double bestEpisodeReward = Double.NEGATIVE_INFINITY;
        double rewardSum = 0.0;

        System.out.println("Plain Discrete AdvSAC training started");
        System.out.println("Workload: " + AdvsacConfig.WORKLOAD_MODEL);
        System.out.println("Episodes: " + AdvsacConfig.TRAINING_EPISODES);

        for (int episode = 1; episode <= AdvsacConfig.TRAINING_EPISODES; episode++) {
            AdvsacCloudSimEnvironment environment = new AdvsacCloudSimEnvironment();
            AdvsacState state = environment.reset();
            AdvsacEpisodeMetrics metrics = new AdvsacEpisodeMetrics();
            boolean done = false;

            while (!done) {
                int actionIndex = agent.selectAction(state, true);
                AdvsacTransition transition = environment.step(actionIndex);
                agent.remember(state, actionIndex, transition.getReward(),
                        transition.getNextState(), transition.isDone());
                agent.trainStep();
                metrics.record(state, transition);
                state = transition.getNextState();
                done = transition.isDone();
            }

            double episodeReward = metrics.getTotalReward();
            rewardSum += episodeReward;
            bestEpisodeReward = Math.max(bestEpisodeReward, episodeReward);

            System.out.printf(
                    "EPISODE %d/%d | workload=%s | steps=%d | reward=%.6f"
                            + " | avgReward=%.6f | bestReward=%.6f"
                            + " | avgCPU=%.4f | avgMemory=%.4f"
                            + " | SLA-met-step-rate=%.4f | scaleUp=%d | scaleDown=%d"
                            + " | replay=%d%n",
                    episode, AdvsacConfig.TRAINING_EPISODES,
                    AdvsacConfig.WORKLOAD_MODEL, metrics.getSteps(), episodeReward,
                    rewardSum / episode, bestEpisodeReward,
                    metrics.getAverageCpuUtilization(),
                    metrics.getAverageMemoryUtilization(),
                    metrics.getSlaMetStepRate(),
                    metrics.getScaleUpOperations(), metrics.getScaleDownOperations(),
                    agent.getReplayBufferSize());
        }

        System.out.println("Plain Discrete AdvSAC training completed.");
    }
}
