package org.cloudbus.cloudsim.examples.autoscaling.controller;

import org.cloudbus.cloudsim.examples.autoscaling.state.CloudState;

/**
 * Common interface for all autoscaling controllers.
 *
 * The controller observes the current CloudState
 * and produces a scaling action.
 *
 * Action:
 *   -1 = scale down by one VM
 *    0 = maintain current capacity
 *   +1 = scale up by one VM
 */
public interface AutoscalingController {

    /**
     * Decide the scaling action based on the current cloud state.
     *
     * @param state current cloud state
     * @return scaling action (-1, 0, +1)
     */
    int decide(CloudState state);
}