package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

/**
 * RL environment contract for the CloudSim autoscaling experiment.
 *
 * The implementation will own CloudSim simulation state.
 * The AdvSAC agent will interact only through reset and step.
 */
public interface AdvsacEnvironment {

    /**
     * Starts a new complete CloudSim episode and returns S0.
     */
    AdvsacState reset();

    /**
     * Applies one of the five discrete scaling actions and advances
     * the CloudSim simulation by the fixed RL timestep.
     */
    AdvsacTransition step(int actionIndex);
}
