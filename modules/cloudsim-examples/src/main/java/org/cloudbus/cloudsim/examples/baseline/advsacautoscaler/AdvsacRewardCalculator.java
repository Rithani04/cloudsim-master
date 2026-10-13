package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * Computes a simple ASTRA-inspired reward for the plain baseline.
 *
 * Reward encourages CPU utilization while applying a continuous, bounded
 * penalty when average response time exceeds the SLO. This avoids a
 * discontinuous reward drop immediately above the SLO. No uncertainty,
 * distribution-shift, adversarial or risk term is used.
 */
public final class AdvsacRewardCalculator {

    /**
     * Computes one reward observation.
     *
     * @param averageCpuUtilization average CPU utilization in [0, 1]
     * @param averageResponseTime average response time in seconds
     * @param activeVmCount number of active VMs after the action
     * @return scalar reward, finite for valid inputs
     */
    public double calculate(
            final double averageCpuUtilization,
            final double averageResponseTime,
            final int activeVmCount,
            final boolean constraintViolation) {

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

        double normalizedSlaViolation = Math.max(
                0.0,
                (averageResponseTime - AdvsacConfig.SLO_SECONDS)
                        / AdvsacConfig.SLO_SECONDS);
        // Cap the SLA penalty at 1.0 to keep extreme latency from dominating
        // the reward without bound. The penalty is continuous at the SLO.
        normalizedSlaViolation = Math.min(1.0, normalizedSlaViolation);
        double reward = averageCpuUtilization - normalizedSlaViolation;

        if (constraintViolation
                || activeVmCount < AdvsacConfig.MIN_VM_COUNT
                || activeVmCount > AdvsacConfig.MAX_VM_COUNT) {
            reward += AdvsacConfig.INSTANCE_CONSTRAINT_PENALTY;
        }

        return reward;
    }

    private boolean isFinite(final double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
