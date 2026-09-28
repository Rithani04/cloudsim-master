package org.cloudbus.cloudsim.examples.autoscaling.controller;

import org.cloudbus.cloudsim.examples.autoscaling.AutoscalingConfig;
import org.cloudbus.cloudsim.examples.autoscaling.state.CloudState;

/**
 * Traditional threshold-based autoscaling controller.
 *
 * Policy:
 *
 * CPU > 70% for N consecutive intervals
 *      -> scale up by one VM
 *
 * CPU < 30% for N consecutive intervals
 *      -> scale down by one VM
 *
 * Otherwise
 *      -> maintain current capacity
 */
public class ThresholdAutoscaler
        implements AutoscalingController {

    /**
     * Upper CPU threshold.
     */
    private final double scaleUpThreshold;

    /**
     * Lower CPU threshold.
     */
    private final double scaleDownThreshold;

    /**
     * Number of consecutive intervals required
     * before taking a scaling action.
     */
    private final int requiredConsecutiveIntervals;

    /**
     * Number of consecutive high-CPU observations.
     */
    private int highCpuCount;

    /**
     * Number of consecutive low-CPU observations.
     */
    private int lowCpuCount;


    /**
     * Create a threshold autoscaler using the
     * project's baseline parameters.
     *
     * The exact value of N is kept configurable.
     */
    public ThresholdAutoscaler(
            double scaleUpThreshold,
            double scaleDownThreshold,
            int requiredConsecutiveIntervals) {

        if (scaleUpThreshold <= scaleDownThreshold) {
            throw new IllegalArgumentException(
                    "Scale-up threshold must be greater than " +
                            "scale-down threshold."
            );
        }

        if (requiredConsecutiveIntervals <= 0) {
            throw new IllegalArgumentException(
                    "Required consecutive intervals must be positive."
            );
        }

        this.scaleUpThreshold =
                scaleUpThreshold;

        this.scaleDownThreshold =
                scaleDownThreshold;

        this.requiredConsecutiveIntervals =
                requiredConsecutiveIntervals;

        this.highCpuCount = 0;
        this.lowCpuCount = 0;
    }


    /**
     * Create the baseline using the project's
     * 70% / 30% threshold policy.
     *
     * N is currently set to 3 decision intervals.
     */
    public ThresholdAutoscaler() {

        this(
                70.0,
                30.0,
                3
        );
    }


    /**
     * Decide whether to scale up, scale down,
     * or maintain the current capacity.
     *
     * @param state current cloud state
     * @return -1, 0, or +1
     */
    @Override
    public int decide(CloudState state) {

        if (state == null) {
            throw new IllegalArgumentException(
                    "Cloud state cannot be null."
            );
        }

        double cpu =
                state.getCpuUtilization();


        /*
         * =====================================================
         * HIGH CPU
         * =====================================================
         */

        if (cpu > scaleUpThreshold) {

            highCpuCount++;

            // A high CPU reading breaks the low-CPU streak.
            lowCpuCount = 0;


            if (highCpuCount
                    >= requiredConsecutiveIntervals) {

                // Reset after taking the action.
                highCpuCount = 0;

                return +1;
            }


            return 0;
        }


        /*
         * =====================================================
         * LOW CPU
         * =====================================================
         */

        if (cpu < scaleDownThreshold) {

            lowCpuCount++;

            // A low CPU reading breaks the high-CPU streak.
            highCpuCount = 0;


            if (lowCpuCount
                    >= requiredConsecutiveIntervals) {

                // Reset after taking the action.
                lowCpuCount = 0;

                return -1;
            }


            return 0;
        }


        /*
         * =====================================================
         * NORMAL CPU RANGE
         * =====================================================
         */

        highCpuCount = 0;
        lowCpuCount = 0;

        return 0;
    }


    public double getScaleUpThreshold() {
        return scaleUpThreshold;
    }


    public double getScaleDownThreshold() {
        return scaleDownThreshold;
    }


    public int getRequiredConsecutiveIntervals() {
        return requiredConsecutiveIntervals;
    }


    public int getHighCpuCount() {
        return highCpuCount;
    }


    public int getLowCpuCount() {
        return lowCpuCount;
    }
}