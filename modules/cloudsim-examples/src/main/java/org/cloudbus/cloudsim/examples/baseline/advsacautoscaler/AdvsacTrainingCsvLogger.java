package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Writes one row per training episode so learning curves can be analysed later.
 * The CSV is overwritten at the start of each run to avoid mixing experiments.
 */
public final class AdvsacTrainingCsvLogger implements AutoCloseable {
    private static final String HEADER =
            "episode,workload,steps,cumulative_reward,average_reward,"
            + "running_average_reward,best_reward,average_cpu_utilization,"
            + "average_memory_utilization,mean_normalized_response_time_state,"
            + "mean_normalized_replica_state,sla_met_step_rate,"
            + "scale_up_operations,scale_down_operations,replay_buffer_size,"
            + "adversarial_epsilon,seed,episode_wall_time_seconds";

    private final Path outputPath;
    private final BufferedWriter writer;

    public AdvsacTrainingCsvLogger() throws IOException {
        String configuredPath = System.getProperty("advsac.metrics.csv", "");
        String defaultPath = "results/advsac-training-" 
                + AdvsacConfig.WORKLOAD_MODEL.name().toLowerCase() + ".csv";
        outputPath = Paths.get(configuredPath.isBlank() ? defaultPath : configuredPath);
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        writer.write(HEADER);
        writer.newLine();
        writer.flush();
        System.out.println("Episode metrics CSV: " + outputPath.toAbsolutePath());
    }

    public void logEpisode(
            final int episode,
            final AdvsacEpisodeMetrics metrics,
            final double runningAverageReward,
            final double bestReward,
            final int replayBufferSize,
            final double episodeWallTimeSeconds) throws IOException {
        writer.write(String.format(java.util.Locale.ROOT,
                "%d,%s,%d,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%d,%d,%d,%.6f,%d,%.6f",
                episode,
                AdvsacConfig.WORKLOAD_MODEL.name(),
                metrics.getSteps(),
                metrics.getTotalReward(),
                metrics.getAverageReward(),
                runningAverageReward,
                bestReward,
                metrics.getAverageCpuUtilization(),
                metrics.getAverageMemoryUtilization(),
                metrics.getAverageResponseTimeState(),
                metrics.getAverageReplicaState(),
                metrics.getSlaMetStepRate(),
                metrics.getScaleUpOperations(),
                metrics.getScaleDownOperations(),
                replayBufferSize,
                AdvsacConfig.ADVERSARIAL_EPSILON,
                AdvsacConfig.RANDOM_SEED,
                episodeWallTimeSeconds));
        writer.newLine();
        writer.flush();
    }

    public Path getOutputPath() {
        return outputPath;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
