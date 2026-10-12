package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.examples.baseline.common.AutoscalingConfig;
import org.cloudbus.cloudsim.examples.baseline.common.broker.DynamicCloudletBroker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates request arrivals inside the CloudSim simulation.
 *
 * The four workload models change arrival patterns while keeping
 * the cloudlet size and infrastructure fixed, allowing controller
 * comparisons without changing several variables at once.
 */
public final class AdvsacWorkloadGenerator {

    private static final double STABLE_MEAN_INTERARRIVAL = 4.5;
    private static final double PERIODIC_LOW_INTERARRIVAL = 7.0;
    private static final double PERIODIC_HIGH_INTERARRIVAL = 2.5;
    private static final double GRADUAL_START_INTERARRIVAL = 8.0;
    private static final double GRADUAL_END_INTERARRIVAL = 1.5;
    private static final double BURST_START_TIME = 700.0;
    private static final double BURST_NORMAL_INTERARRIVAL = 7.0;
    private static final double BURST_INTERARRIVAL = 1.2;
    private static final int MAX_REQUESTS_PER_EPISODE = 5000;

    private final Random random;

    public AdvsacWorkloadGenerator(final long seed) {
        random = new Random(seed);
    }

    /**
     * Creates cloudlets and schedules their arrival events through
     * DynamicCloudletBroker. The returned list contains all generated
     * cloudlets, including requests that may not finish before the
     * episode ends.
     */
    public List<Cloudlet> generate(
            final DynamicCloudletBroker broker,
            final int brokerId,
            final AdvsacConfig.WorkloadModel workloadModel) {

        if (broker == null || workloadModel == null) {
            throw new IllegalArgumentException("Broker and workload model are required.");
        }

        List<Cloudlet> generated = new ArrayList<>();
        UtilizationModel utilization = new UtilizationModelFull();

        double arrivalTime = 0.0;
        int cloudletId = 0;

        while (arrivalTime <= AdvsacConfig.EPISODE_DURATION
                && cloudletId < MAX_REQUESTS_PER_EPISODE) {

            Cloudlet cloudlet = new Cloudlet(
                    cloudletId,
                    AutoscalingConfig.CLOUDLET_LENGTH,
                    AutoscalingConfig.VM_PES,
                    AutoscalingConfig.CLOUDLET_FILE_SIZE,
                    AutoscalingConfig.CLOUDLET_OUTPUT_SIZE,
                    utilization,
                    utilization,
                    utilization);

            cloudlet.setUserId(brokerId);
            generated.add(cloudlet);

            List<Cloudlet> singleCloudlet = new ArrayList<>(1);
            singleCloudlet.add(cloudlet);
            broker.submitCloudletList(singleCloudlet, arrivalTime);
            broker.registerScheduledCloudlets(1);

            cloudletId++;
            arrivalTime += sampleInterarrival(workloadModel, arrivalTime);
        }

        return generated;
    }

    private double sampleInterarrival(
            final AdvsacConfig.WorkloadModel model,
            final double currentArrivalTime) {

        switch (model) {
            case STABLE:
                return exponentialInterval(STABLE_MEAN_INTERARRIVAL);

            case PERIODIC:
                long phase = (long) (currentArrivalTime / 200.0);
                double periodicMean = phase % 2 == 0
                        ? PERIODIC_LOW_INTERARRIVAL
                        : PERIODIC_HIGH_INTERARRIVAL;
                return exponentialInterval(periodicMean);

            case GRADUAL_INCREASE:
                double progress = Math.min(
                        1.0,
                        currentArrivalTime / AdvsacConfig.EPISODE_DURATION);
                double mean = GRADUAL_START_INTERARRIVAL
                        + progress * (GRADUAL_END_INTERARRIVAL
                        - GRADUAL_START_INTERARRIVAL);
                return exponentialInterval(mean);

            case SUDDEN_BURST:
                double burstMean = currentArrivalTime < BURST_START_TIME
                        ? BURST_NORMAL_INTERARRIVAL
                        : BURST_INTERARRIVAL;
                return exponentialInterval(burstMean);

            default:
                throw new IllegalArgumentException("Unsupported workload model: " + model);
        }
    }

    private double exponentialInterval(final double mean) {
        // nextDouble() can return zero, so clamp away from log(0).
        double uniform = Math.max(1.0e-12, random.nextDouble());
        return -mean * Math.log(uniform);
    }
}
