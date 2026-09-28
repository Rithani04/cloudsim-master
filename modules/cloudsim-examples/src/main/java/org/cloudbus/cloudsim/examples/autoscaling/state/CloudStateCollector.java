package org.cloudbus.cloudsim.examples.autoscaling.state;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * Collects the current state of the cloud environment.
 *
 * Person 1 responsibility:
 * - Observe workload
 * - Observe CPU utilization
 * - Observe active VM count
 * - Observe queue
 * - Observe response time
 * - Observe SLA violations
 *
 * This class does NOT make scaling decisions.
 */
public class CloudStateCollector {

    /**
     * Collect the current state of the cloud.
     */
    public CloudState collectState(
            double currentTime,
            double workload,
            double cpuUtilization,
            int activeVmCount,
            List<Cloudlet> cloudletList) {

        int queueLength =
                calculateQueueLength(cloudletList);

        double responseTime =
                calculateAverageResponseTime(cloudletList);

        double slaViolation =
                calculateSlaViolation(cloudletList);

        return new CloudState(
                currentTime,
                workload,
                cpuUtilization,
                activeVmCount,
                queueLength,
                responseTime,
                slaViolation
        );
    }


    /**
     * Count cloudlets that have not completed.
     */
    private int calculateQueueLength(
            List<Cloudlet> cloudletList) {

        if (cloudletList == null) {
            return 0;
        }

        int queueLength = 0;

        for (Cloudlet cloudlet : cloudletList) {

            if (cloudlet.getStatus()
                    != Cloudlet.CloudletStatus.SUCCESS) {

                queueLength++;
            }
        }

        return queueLength;
    }


    /**
     * Calculate average response time
     * of completed cloudlets.
     */
    private double calculateAverageResponseTime(
            List<Cloudlet> cloudletList) {

        if (cloudletList == null ||
                cloudletList.isEmpty()) {

            return 0.0;
        }

        double totalResponseTime = 0.0;

        int completedCloudlets = 0;

        for (Cloudlet cloudlet : cloudletList) {

            if (cloudlet.getStatus()
                    == Cloudlet.CloudletStatus.SUCCESS) {

                double responseTime =
                        cloudlet.getExecFinishTime()
                                - cloudlet.getSubmissionTime();

                totalResponseTime += responseTime;

                completedCloudlets++;
            }
        }

        if (completedCloudlets == 0) {
            return 0.0;
        }

        return totalResponseTime
                / completedCloudlets;
    }


    /**
     * Calculate the percentage of completed
     * cloudlets violating the SLA.
     *
     * Temporary SLA definition:
     * response time > 10 seconds.
     */
    private double calculateSlaViolation(
            List<Cloudlet> cloudletList) {

        if (cloudletList == null ||
                cloudletList.isEmpty()) {

            return 0.0;
        }

        int violations = 0;
        int completedCloudlets = 0;

        for (Cloudlet cloudlet : cloudletList) {

            if (cloudlet.getStatus()
                    == Cloudlet.CloudletStatus.SUCCESS) {

                completedCloudlets++;

                double responseTime =
                        cloudlet.getExecFinishTime()
                                - cloudlet.getSubmissionTime();

                if (responseTime > 10.0) {
                    violations++;
                }
            }
        }

        if (completedCloudlets == 0) {
            return 0.0;
        }

        return (double) violations
                / completedCloudlets;
    }
}