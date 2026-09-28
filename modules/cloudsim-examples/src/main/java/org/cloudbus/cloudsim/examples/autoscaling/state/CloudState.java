package org.cloudbus.cloudsim.examples.autoscaling.state;

/**
 * Represents the current state of the cloud environment
 * at a particular decision time.
 */
public class CloudState {

    private final double time;
    private final double workload;
    private final double cpuUtilization;
    private final int activeVmCount;
    private final int queueLength;
    private final double responseTime;
    private final double slaViolation;

    public CloudState(
            double time,
            double workload,
            double cpuUtilization,
            int activeVmCount,
            int queueLength,
            double responseTime,
            double slaViolation) {

        this.time = time;
        this.workload = workload;
        this.cpuUtilization = cpuUtilization;
        this.activeVmCount = activeVmCount;
        this.queueLength = queueLength;
        this.responseTime = responseTime;
        this.slaViolation = slaViolation;
    }

    public double getTime() {
        return time;
    }

    public double getWorkload() {
        return workload;
    }

    public double getCpuUtilization() {
        return cpuUtilization;
    }

    public int getActiveVmCount() {
        return activeVmCount;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public double getSlaViolation() {
        return slaViolation;
    }

    @Override
    public String toString() {

        return String.format(
                "Time=%.1f | Workload=%.2f | CPU=%.2f%% | ActiveVMs=%d | Queue=%d | ResponseTime=%.2f | SLA=%.2f",
                time,
                workload,
                cpuUtilization,
                activeVmCount,
                queueLength,
                responseTime,
                slaViolation
        );
    }
}