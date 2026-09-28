package org.cloudbus.cloudsim.examples.autoscaling;

/**
 * Traditional threshold-based autoscaling controller.
 *
 * This is the baseline controller for the research project.
 *
 * No reinforcement learning is used here.
 * No uncertainty estimation is used here.
 * No distribution-shift detection is used here.
 */
public class BaselineAutoscaler {

    private int currentInstances;

    private double lastScalingTime;

    public BaselineAutoscaler() {

        currentInstances =
                AutoscalingConfig.INITIAL_INSTANCES;

        lastScalingTime = -Double.MAX_VALUE;
    }


    public int getCurrentInstances() {

        return currentInstances;
    }


    /**
     * Decide the desired number of VM instances.
     *
     * @param currentTime current simulation time
     * @param utilization current workload/utilization
     * @return desired VM count
     */
    public int decide(
            double currentTime,
            double utilization) {

        /*
         * Prevent rapid repeated scaling.
         */
        if (currentTime - lastScalingTime
                < AutoscalingConfig.SCALE_COOLDOWN) {

            return currentInstances;
        }


        /*
         * SCALE UP
         */
        if (utilization
                > AutoscalingConfig.SCALE_UP_THRESHOLD) {

            if (currentInstances
                    < AutoscalingConfig.MAX_INSTANCES) {

                currentInstances++;

                lastScalingTime = currentTime;
            }
        }


        /*
         * SCALE DOWN
         */
        else if (utilization
                < AutoscalingConfig.SCALE_DOWN_THRESHOLD) {

            if (currentInstances
                    > AutoscalingConfig.MIN_INSTANCES) {

                currentInstances--;

                lastScalingTime = currentTime;
            }
        }


        return currentInstances;
    }
}