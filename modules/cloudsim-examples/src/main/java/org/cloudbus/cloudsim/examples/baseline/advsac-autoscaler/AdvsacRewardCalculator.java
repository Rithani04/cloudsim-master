package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Computes the initial ASTRA-style reward for the plain baseline.
 *
 * Reward uses average CPU utilization and average response time.
 * No uncertainty, distribution-shift, adversarial or risk term is used.
 */
public final class AdvsacRewardCalculator {

    /**
     * Computes one reward observation.
     *
     * @param averageCpuUtilization average CPU utilization in [0, 1]
     * @param averageResponseTime average response time in seconds
     * @param activeVmCount number of active VMs after the action
     * @return finite scalar reward
     */
    public double calculate(
            final double averageCpuUtilization,
            final double averageResponseTime,
            final int activeVmCount) {

        if (!isFinite(averageCpuUtilization)
                || averageCpuUtilization < 0.0
                || averageCpuUtilization > 1.0) {
            throw new IllegalArgumentException(
                    "CPU utilization must be finite and in [0,1].");
        }

        if (!isFinite(averageResponseTime) || averageResponseTime < 0.0) {
            throw new IllegalArgumentException(
                    "Response time must be finite and non-negative.");
        }

        double reward;
        if (averageResponseTime <= AdvsacConfig.SLO_SECONDS) {
            reward = averageCpuUtilization;
        } else {
            reward = averageCpuUtilization
                    - (AdvsacConfig.SLO_SECONDS / averageResponseTime);
        }

        if (activeVmCount < AdvsacConfig.MIN_VM_COUNT
                || activeVmCount > AdvsacConfig.MAX_VM_COUNT) {
            reward += AdvsacConfig.INSTANCE_CONSTRAINT_PENALTY;
        }

        return reward;
    }

    private boolean isFinite(final double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
