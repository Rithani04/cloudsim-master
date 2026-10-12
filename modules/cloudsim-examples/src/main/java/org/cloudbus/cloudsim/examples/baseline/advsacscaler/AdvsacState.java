package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.Arrays;

/**
 * Eight-dimensional normalized state used by Plain Discrete AdvSAC.
 *
 * Ordering:
 * 0 CPU Utilization
 * 1 Memory Utilization
 * 2 Workload
 * 3 Workload Growth
 * 4 Queue
 * 5 Response Time
 * 6 Replica Count
 * 7 SLA
 *
 * All values must be normalized to [0, 1].
 */
public final class AdvsacState {

    public static final int CPU_UTILIZATION = 0;
    public static final int MEMORY_UTILIZATION = 1;
    public static final int WORKLOAD = 2;
    public static final int WORKLOAD_GROWTH = 3;
    public static final int QUEUE = 4;
    public static final int RESPONSE_TIME = 5;
    public static final int REPLICA_COUNT = 6;
    public static final int SLA = 7;

    public static final int DIMENSION = 8;

    private final double[] values;

    public AdvsacState(final double[] values) {
        if (values == null || values.length != DIMENSION) {
            throw new IllegalArgumentException("State must contain exactly 8 values.");
        }

        this.values = Arrays.copyOf(values, values.length);

        for (int i = 0; i < this.values.length; i++) {
            if (Double.isNaN(this.values[i])
                    || Double.isInfinite(this.values[i])
                    || this.values[i] < 0.0
                    || this.values[i] > 1.0) {
                throw new IllegalArgumentException(
                        "State value at index " + i + " must be in [0,1].");
            }
        }
    }

    public double get(final int index) {
        return values[index];
    }

    public double getCpuUtilization() {
        return values[CPU_UTILIZATION];
    }

    public double getMemoryUtilization() {
        return values[MEMORY_UTILIZATION];
    }

    public double getWorkload() {
        return values[WORKLOAD];
    }

    public double getWorkloadGrowth() {
        return values[WORKLOAD_GROWTH];
    }

    public double getQueue() {
        return values[QUEUE];
    }

    public double getResponseTime() {
        return values[RESPONSE_TIME];
    }

    public double getReplicaCount() {
        return values[REPLICA_COUNT];
    }

    public double getSla() {
        return values[SLA];
    }

    public double[] toArray() {
        return Arrays.copyOf(values, values.length);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
