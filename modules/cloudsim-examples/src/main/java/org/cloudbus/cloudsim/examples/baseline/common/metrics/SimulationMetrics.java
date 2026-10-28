package org.cloudbus.cloudsim.examples.baseline.common.metrics;

import org.cloudbus.cloudsim.Cloudlet;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared metric collector for threshold and future RL controllers. */
public final class SimulationMetrics {

    private static final class Sample {
        final double time;
        final double cpuPct;
        final double memoryPct;
        final int activeVms;

        Sample(double time, double cpuPct, double memoryPct, int activeVms) {
            this.time = time;
            this.cpuPct = cpuPct;
            this.memoryPct = memoryPct;
            this.activeVms = activeVms;
        }
    }

    private static final class Scaling {
        final double time;
        final int before;
        final int after;

        Scaling(double time, int before, int after) {
            this.time = time;
            this.before = before;
            this.after = after;
        }
    }

    private final List<Sample> samples = new ArrayList<>();
    private final List<Scaling> scaling = new ArrayList<>();
    private final Map<Integer, Double> arrivals = new HashMap<>();

    /** CPU is recorded as the supplied CPU-demand percentage; it may exceed 100%. */
    public void recordSample(double time, double cpuPercent, double memoryPercent, int activeVms) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Sample time must be finite and nonnegative: " + time);
        }
        if (!samples.isEmpty() && time <= samples.get(samples.size() - 1).time) {
            throw new IllegalArgumentException("Sample times must strictly increase");
        }
        if (activeVms < 0) {
            throw new IllegalArgumentException("Active VM count cannot be negative");
        }
        samples.add(new Sample(time, cpuPercent, memoryPercent, activeVms));
    }

    /** Record an observed change in provisioned VM count. */
    public void recordScaling(double time, int before, int after) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Scaling time must be finite and nonnegative");
        }
        if (before != after) {
            scaling.add(new Scaling(time, before, after));
        }
    }

    /** Record actual cloudlet arrival time, not merely creation time. */
    public void recordArrival(int cloudletId, double arrivalTime) {
        if (!Double.isFinite(arrivalTime) || arrivalTime < 0) {
            throw new IllegalArgumentException("Arrival time must be finite and nonnegative");
        }
        arrivals.put(cloudletId, arrivalTime);
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double position = (sorted.size() - 1) * fraction;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return sorted.get(lower) + (position - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.6f", value) : "NA";
    }

    private static void csvRow(Writer writer, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            String value = values[i] == null ? "" : values[i];
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write('\n');
    }

    private enum Measure { CPU, MEMORY, VMS }

    /**
     * Time-weighted average over intervals with valid measurements.
     * Missing CPU/memory samples do not poison the entire average.
     * The denominator includes only intervals with a valid value for that measure.
     */
    private double weightedAverage(Measure measure, double endTime) {
        double area = 0;
        double validDuration = 0;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double nextTime = i + 1 < samples.size() ? samples.get(i + 1).time : endTime;
            double duration = Math.min(nextTime, endTime) - sample.time;
            if (!(duration > 0) || !Double.isFinite(duration)) continue;

            double value;
            switch (measure) {
                case CPU: value = sample.cpuPct; break;
                case MEMORY: value = sample.memoryPct; break;
                default: value = sample.activeVms; break;
            }
            if (!Double.isFinite(value)) continue;
            area += duration * value;
            validDuration += duration;
        }
        return validDuration > 0 ? area / validDuration : Double.NaN;
    }

    /** VM-time computed from the same provisioned-VM samples used for the average. */
    private double calculateVmTime(double endTime) {
        double vmTime = 0;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double nextTime = i + 1 < samples.size() ? samples.get(i + 1).time : endTime;
            double duration = Math.min(nextTime, endTime) - sample.time;
            if (Double.isFinite(duration) && duration > 0) {
                vmTime += duration * sample.activeVms;
            }
        }
        return vmTime;
    }

    /** Unit cost is per provisioned VM per simulation-time unit, not a cloud bill. */
    public void export(Path directory, String controller, String regime,
                       Collection<Cloudlet> completed, int totalSubmitted,
                       double slaLimit, double costPerVmPerTimeUnit,
                       double observationEndTime) throws IOException {
        if (!Double.isFinite(observationEndTime) || observationEndTime < 0) {
            throw new IllegalArgumentException("Observation end time must be finite and nonnegative");
        }
        Files.createDirectories(directory);

        List<Double> latencies = new ArrayList<>();
        int successful = 0;
        int violations = 0;
        int missingArrival = 0;

        try (Writer writer = Files.newBufferedWriter(directory.resolve("cloudlets.csv"), StandardCharsets.UTF_8)) {
            csvRow(writer, "id", "status", "arrival", "start", "finish", "response_time", "sla_violation");
            for (Cloudlet cloudlet : completed) {
                if (cloudlet.getStatus() != Cloudlet.CloudletStatus.SUCCESS) continue;
                successful++;
                Double arrival = arrivals.get(cloudlet.getCloudletId());
                double finish = cloudlet.getExecFinishTime();
                double response = arrival == null ? Double.NaN : finish - arrival;
                boolean valid = Double.isFinite(response) && response >= 0;
                if (valid) {
                    latencies.add(response);
                    if (response > slaLimit) violations++;
                } else {
                    missingArrival++;
                }
                csvRow(writer, Integer.toString(cloudlet.getCloudletId()), "SUCCESS",
                        arrival == null ? "NA" : fmt(arrival),
                        fmt(cloudlet.getExecStartTime()), fmt(finish), fmt(response),
                        valid ? Boolean.toString(response > slaLimit) : "NA");
            }
        }

        double vmTime = calculateVmTime(observationEndTime);
        double averageCpu = weightedAverage(Measure.CPU, observationEndTime);
        double averageMemory = weightedAverage(Measure.MEMORY, observationEndTime);
        double averageVms = weightedAverage(Measure.VMS, observationEndTime);

        int reversals = 0;
        int previousDirection = 0;
        for (Scaling event : scaling) {
            int direction = Integer.signum(event.after - event.before);
            if (previousDirection != 0 && direction != previousDirection) reversals++;
            previousDirection = direction;
        }

        try (Writer writer = Files.newBufferedWriter(directory.resolve("timeseries.csv"), StandardCharsets.UTF_8)) {
            csvRow(writer, "time", "cpu_percent", "memory_percent", "active_vms");
            for (Sample sample : samples) {
                csvRow(writer, fmt(sample.time), fmt(sample.cpuPct), fmt(sample.memoryPct),
                        Integer.toString(sample.activeVms));
            }
        }

        try (Writer writer = Files.newBufferedWriter(directory.resolve("scaling_events.csv"), StandardCharsets.UTF_8)) {
            csvRow(writer, "time", "before_vms", "after_vms", "delta");
            for (Scaling event : scaling) {
                csvRow(writer, fmt(event.time), Integer.toString(event.before),
                        Integer.toString(event.after), Integer.toString(event.after - event.before));
            }
        }

        try (Writer writer = Files.newBufferedWriter(directory.resolve("summary.csv"), StandardCharsets.UTF_8)) {
            csvRow(writer, "controller", "workload", "submitted", "completed_success", "completion_rate_percent",
                    "latency_sample_count", "missing_arrival_count", "avg_response_time", "p95_response_time",
                    "sla_limit", "sla_violation_rate_completed_percent", "sla_miss_or_unfinished_percent",
                    "avg_cpu_percent", "avg_memory_percent", "avg_active_vms", "vm_time", "estimated_cost",
                    "scaling_events", "scaling_oscillations");

            csvRow(writer, controller, regime, Integer.toString(totalSubmitted), Integer.toString(successful),
                    fmt(totalSubmitted > 0 ? 100.0 * successful / totalSubmitted : Double.NaN),
                    Integer.toString(latencies.size()), Integer.toString(missingArrival),
                    fmt(mean(latencies)), fmt(percentile(latencies, 0.95)), fmt(slaLimit),
                    fmt(latencies.isEmpty() ? Double.NaN : 100.0 * violations / latencies.size()),
                    fmt(totalSubmitted > 0 && missingArrival == 0
                            ? 100.0 * (violations + Math.max(0, totalSubmitted - successful)) / totalSubmitted
                            : Double.NaN),
                    fmt(averageCpu), fmt(averageMemory), fmt(averageVms), fmt(vmTime),
                    fmt(vmTime * costPerVmPerTimeUnit), Integer.toString(scaling.size()), Integer.toString(reversals));
        }

        System.out.printf(Locale.US,
                "Metrics: samples=%d, avgCPU=%s, avgVMs=%s, VM-time=%s%n",
                samples.size(), fmt(averageCpu), fmt(averageVms), fmt(vmTime));
    }
}
