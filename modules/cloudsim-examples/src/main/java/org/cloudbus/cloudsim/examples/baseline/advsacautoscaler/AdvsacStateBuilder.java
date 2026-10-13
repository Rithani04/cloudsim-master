package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * Converts raw CloudSim observations into the fixed eight-element
 * normalized AdvSAC state vector.
 */
public final class AdvsacStateBuilder {

    private static final double QUEUE_NORMALIZATION_CAPACITY = 100.0;
    private static final double REQUESTS_PER_WINDOW_NORMALIZATION_CAPACITY = 10.0;

    private double lastAverageCpuUtilization;
    private double lastAverageMemoryUtilization;
    private double lastAverageResponseTime;
    private int lastQueueLength;
    private double lastWorkloadRate;

    public AdvsacState build(
            final double currentTime,
            final List<Vm> activeVms,
            final List<Cloudlet> cloudlets,
            final List<Double> arrivalTimes,
            final int waitingQueueLength) {

        if (activeVms == null || cloudlets == null || arrivalTimes == null
                || cloudlets.size() != arrivalTimes.size()) {
            throw new IllegalArgumentException(
                    "VMs, cloudlets and aligned arrival timestamps are required.");
        }

        lastAverageCpuUtilization = calculateAverageCpuUtilization(activeVms, currentTime);
        lastAverageMemoryUtilization = calculateAverageMemoryUtilization(activeVms, currentTime);
        double recentResponseTime = calculateRecentAverageResponseTime(cloudlets, currentTime);
        if (recentResponseTime >= 0.0) {
            lastAverageResponseTime = recentResponseTime;
        }
        lastQueueLength = Math.max(0, waitingQueueLength);

        int recentArrivals = countArrivals(
                currentTime - AdvsacConfig.RL_TIMESTEP,
                currentTime,
                true,
                arrivalTimes);
        int previousArrivals = countArrivals(
                currentTime - (2.0 * AdvsacConfig.RL_TIMESTEP),
                currentTime - AdvsacConfig.RL_TIMESTEP,
                false,
                arrivalTimes);

        lastWorkloadRate = recentArrivals / AdvsacConfig.RL_TIMESTEP;
        double normalizedWorkload = clamp(
                recentArrivals / REQUESTS_PER_WINDOW_NORMALIZATION_CAPACITY);
        double normalizedGrowth;
        if (recentArrivals + previousArrivals == 0) {
            normalizedGrowth = 0.5;
        } else {
            double signedGrowth = (double) (recentArrivals - previousArrivals)
                    / (recentArrivals + previousArrivals);
            normalizedGrowth = clamp((signedGrowth + 1.0) / 2.0);
        }

        double normalizedResponseTime = clamp(
                lastAverageResponseTime / (2.0 * AdvsacConfig.SLO_SECONDS));
        double normalizedReplicaCount = AdvsacConfig.MAX_VM_COUNT
                == AdvsacConfig.MIN_VM_COUNT
                ? 0.0
                : (double) (activeVms.size() - AdvsacConfig.MIN_VM_COUNT)
                / (AdvsacConfig.MAX_VM_COUNT - AdvsacConfig.MIN_VM_COUNT);

        double slaMet = lastAverageResponseTime <= AdvsacConfig.SLO_SECONDS ? 1.0 : 0.0;

        return new AdvsacState(new double[] {
                clamp(lastAverageCpuUtilization),
                clamp(lastAverageMemoryUtilization),
                normalizedWorkload,
                normalizedGrowth,
                clamp(lastQueueLength / QUEUE_NORMALIZATION_CAPACITY),
                normalizedResponseTime,
                clamp(normalizedReplicaCount),
                slaMet
        });
    }

    public double getLastAverageCpuUtilization() {
        return lastAverageCpuUtilization;
    }

    public double getLastAverageMemoryUtilization() {
        return lastAverageMemoryUtilization;
    }

    public double getLastAverageResponseTime() {
        return lastAverageResponseTime;
    }

    public int getLastQueueLength() {
        return lastQueueLength;
    }

    public double getLastWorkloadRate() {
        return lastWorkloadRate;
    }

    private double calculateAverageCpuUtilization(
            final List<Vm> activeVms,
            final double currentTime) {
        if (activeVms.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (Vm vm : activeVms) {
            double utilization = vm.getTotalUtilizationOfCpu(currentTime);

            // CloudSim's aggregate VM utilization can exceed 1.0 when
            // multiple cloudlets execute concurrently. The RL state and
            // reward both define CPU utilization as a normalized value.
            if (Double.isNaN(utilization) || Double.isInfinite(utilization)
                    || utilization < 0.0) {
                utilization = 0.0;
            } else if (utilization > 1.0) {
                utilization = 1.0;
            }

            total += utilization;
        }
        return total / activeVms.size();
    }

    private double calculateAverageMemoryUtilization(
            final List<Vm> activeVms,
            final double currentTime) {
        // Before CloudSim starts, VMs have not been provisioned and no workload
        // is executing. VM requested-RAM defaults can misleadingly look full.
        if (currentTime <= 0.0 || activeVms.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int count = 0;
        for (Vm vm : activeVms) {
            if (vm.getRam() > 0) {
                total += (double) vm.getCurrentRequestedRam() / vm.getRam();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    /**
     * Calculates response time for cloudlets completed in the latest RL
     * window. Returns -1 when the window has no completions so the previous
     * observation can be carried forward rather than treating missing data
     * as a perfect response time.
     */
    private double calculateRecentAverageResponseTime(
            final List<Cloudlet> cloudlets,
            final double currentTime) {
        double windowStart = currentTime - AdvsacConfig.RL_TIMESTEP;
        double total = 0.0;
        int completed = 0;

        for (Cloudlet cloudlet : cloudlets) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                double finishTime = cloudlet.getExecFinishTime();
                if (finishTime > windowStart && finishTime <= currentTime) {
                    double responseTime = finishTime - cloudlet.getSubmissionTime();
                    if (responseTime >= 0.0 && !Double.isNaN(responseTime)
                            && !Double.isInfinite(responseTime)) {
                        total += responseTime;
                        completed++;
                    }
                }
            }
        }
        return completed == 0 ? -1.0 : total / completed;
    }

    public void reset() {
        lastAverageCpuUtilization = 0.0;
        lastAverageMemoryUtilization = 0.0;
        lastAverageResponseTime = 0.0;
        lastQueueLength = 0;
        lastWorkloadRate = 0.0;
    }

    private int countArrivals(
            final double startInclusive,
            final double end,
            final boolean includeEnd,
            final List<Double> arrivalTimes) {
        int count = 0;
        for (double arrivalTime : arrivalTimes) {
            boolean inWindow = arrivalTime >= startInclusive
                    && (includeEnd ? arrivalTime <= end : arrivalTime < end);
            if (inWindow) {
                count++;
            }
        }
        return count;
    }

    private double clamp(final double value) {
        if (Double.isNaN(value) || value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
