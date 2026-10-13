package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Aggregates episode-level learning and resource-control measurements.
 * Values are accumulated from environment transitions and state observations;
 * metrics not yet exposed by the environment are intentionally not fabricated.
 */
public final class AdvsacEpisodeMetrics {
    private int steps;
    private int scaleUpOperations;
    private int scaleDownOperations;
    private double totalReward;
    private double totalCpuUtilization;
    private double totalMemoryUtilization;
    private double totalResponseTimeState;
    private double totalReplicaState;
    private int slaMetSteps;

    public void record(
            final AdvsacState state,
            final AdvsacTransition transition) {
        if (state == null || transition == null) {
            throw new IllegalArgumentException("State and transition are required.");
        }
        steps++;
        totalReward += transition.getReward();
        totalCpuUtilization += state.getCpuUtilization();
        totalMemoryUtilization += state.getMemoryUtilization();
        totalResponseTimeState += state.getResponseTime();
        totalReplicaState += state.getReplicaCount();
        if (state.getSla() >= 1.0) {
            slaMetSteps++;
        }
        int delta = AdvsacConfig.actionValueFromIndex(transition.getActionIndex());
        if (delta > 0) {
            scaleUpOperations += delta;
        } else if (delta < 0) {
            scaleDownOperations += -delta;
        }
    }

    public int getSteps() { return steps; }
    public double getTotalReward() { return totalReward; }
    public double getAverageReward() { return mean(totalReward); }
    public double getAverageCpuUtilization() { return mean(totalCpuUtilization); }
    public double getAverageMemoryUtilization() { return mean(totalMemoryUtilization); }
    public double getAverageResponseTimeState() { return mean(totalResponseTimeState); }
    public double getAverageReplicaState() { return mean(totalReplicaState); }
    public double getSlaMetStepRate() { return mean(slaMetSteps); }
    public int getScaleUpOperations() { return scaleUpOperations; }
    public int getScaleDownOperations() { return scaleDownOperations; }

    private double mean(final double total) {
        return steps == 0 ? 0.0 : total / steps;
    }
}
