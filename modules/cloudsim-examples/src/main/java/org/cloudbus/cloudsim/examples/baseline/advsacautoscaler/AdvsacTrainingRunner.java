package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.io.IOException;

/**
 * Command-line entry point for training the ASTRA-inspired discrete AdvSAC
 * controller in CloudSim. Select the workload with
 * -Dadvsac.workload=STABLE|PERIODIC|GRADUAL_INCREASE|SUDDEN_BURST.
 * Episode metrics are written to CSV after every episode.
 */
public final class AdvsacTrainingRunner {
    private AdvsacTrainingRunner() { }

    public static void main(final String[] args) throws IOException {
        AdvsacAgent agent = new AdvsacAgent();
        double bestEpisodeReward = Double.NEGATIVE_INFINITY;
        double rewardSum = 0.0;

        System.out.println("ASTRA-inspired discrete AdvSAC training started");
        System.out.println("Workload: " + AdvsacConfig.WORKLOAD_MODEL);
        System.out.println("Episodes: " + AdvsacConfig.TRAINING_EPISODES);

        try (AdvsacTrainingCsvLogger csvLogger = new AdvsacTrainingCsvLogger()) {
            for (int episode = 1; episode <= AdvsacConfig.TRAINING_EPISODES; episode++) {
                long episodeStartNanos = System.nanoTime();
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
                double runningAverageReward = rewardSum / episode;
                double episodeWallTimeSeconds =
                        (System.nanoTime() - episodeStartNanos) / 1_000_000_000.0;

                csvLogger.logEpisode(
                        episode, metrics, runningAverageReward, bestEpisodeReward,
                        agent.getReplayBufferSize(), episodeWallTimeSeconds);

                System.out.printf(
                        "EPISODE %d/%d | workload=%s | steps=%d | reward=%.6f"
                                + " | avgReward=%.6f | bestReward=%.6f"
                                + " | avgCPU=%.4f | avgMemory=%.4f"
                                + " | SLA-met-step-rate=%.4f | scaleUp=%d | scaleDown=%d"
                                + " | replay=%d | episodeTime=%.2fs%n",
                        episode, AdvsacConfig.TRAINING_EPISODES,
                        AdvsacConfig.WORKLOAD_MODEL, metrics.getSteps(), episodeReward,
                        metrics.getAverageReward(), bestEpisodeReward,
                        metrics.getAverageCpuUtilization(),
                        metrics.getAverageMemoryUtilization(),
                        metrics.getSlaMetStepRate(),
                        metrics.getScaleUpOperations(), metrics.getScaleDownOperations(),
                        agent.getReplayBufferSize(), episodeWallTimeSeconds);
            }
            System.out.println("Episode metrics saved to: " + csvLogger.getOutputPath().toAbsolutePath());
        }

        System.out.println("ASTRA-inspired discrete AdvSAC training completed.");
    }
}
