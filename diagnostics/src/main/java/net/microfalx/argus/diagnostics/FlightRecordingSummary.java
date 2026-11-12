package net.microfalx.argus.diagnostics;

import jdk.jfr.consumer.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.FormatterUtils.formatDuration;

/**
 * Extracts a (text) summary from a Java Flight Recorder (JFR) recording.
 * <p>
 * The recording is read in-process, with the JFR consumer API ({@link RecordingFile}), so no external tool
 * (like {@code jfr}) is required. The events are streamed (not loaded in memory) and aggregated into:
 * <ul>
 *     <li>CPU load (JVM and machine)</li>
 *     <li>hot methods (top frame of the execution samples)</li>
 *     <li>allocations (sampled allocation weight per class)</li>
 *     <li>garbage collections (pauses per collector)</li>
 *     <li>lock contention (blocked time per monitor class)</li>
 *     <li>exceptions (throwables created, and per class when the throw events are enabled)</li>
 * </ul>
 * Each section reports at most "top" entries.
 */
final class FlightRecordingSummary {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final String UNKNOWN = "<unknown>";

    private final int top;

    private long eventCount;
    private Instant startTime;
    private Instant endTime;

    private final Map<String, Counter> methods = new HashMap<>();
    private long sampleCount;

    private final Map<String, Counter> allocations = new HashMap<>();
    private long allocatedBytes;

    private final Map<String, Counter> collections = new HashMap<>();

    private final Map<String, Counter> contentions = new HashMap<>();

    private final Map<String, Counter> exceptions = new HashMap<>();
    private long minThrowables = Long.MAX_VALUE;
    private long maxThrowables = Long.MIN_VALUE;

    private final Statistics jvmCpu = new Statistics();
    private final Statistics machineCpu = new Statistics();
    private long maxHeapUsed = -1;

    FlightRecordingSummary(int top) {
        if (top <= 0) throw new IllegalArgumentException("Top must be positive, received " + top);
        this.top = top;
    }

    /**
     * Reads the recording and returns its summary.
     *
     * @param file the recording
     * @return a non-null instance
     * @throws IOException if the recording cannot be read
     */
    String summarize(Path file) throws IOException {
        requireNonNull(file);
        try (RecordingFile recordingFile = new RecordingFile(file)) {
            while (recordingFile.hasMoreEvents()) {
                process(recordingFile.readEvent());
            }
        }
        return render();
    }

    private void process(RecordedEvent event) {
        eventCount++;
        if (startTime == null || event.getStartTime().isBefore(startTime)) startTime = event.getStartTime();
        if (endTime == null || event.getEndTime().isAfter(endTime)) endTime = event.getEndTime();
        switch (event.getEventType().getName()) {
            case "jdk.ExecutionSample" -> processExecutionSample(event);
            case "jdk.ObjectAllocationSample" -> processAllocation(event);
            case "jdk.GarbageCollection" -> processGarbageCollection(event);
            case "jdk.JavaMonitorEnter" -> processContention(event);
            case "jdk.JavaExceptionThrow", "jdk.JavaErrorThrow" -> processException(event);
            case "jdk.ExceptionStatistics" -> processExceptionStatistics(event);
            case "jdk.CPULoad" -> processCpuLoad(event);
            case "jdk.GCHeapSummary" -> processHeapSummary(event);
            default -> {
                // not summarized
            }
        }
    }

    private void processExecutionSample(RecordedEvent event) {
        sampleCount++;
        RecordedStackTrace stackTrace = event.getStackTrace();
        String method = UNKNOWN;
        if (stackTrace != null && !stackTrace.getFrames().isEmpty()) {
            method = getMethodName(stackTrace.getFrames().getFirst().getMethod());
        }
        counter(methods, method).add(1);
    }

    private void processAllocation(RecordedEvent event) {
        if (!event.hasField("objectClass") || !event.hasField("weight")) return;
        long weight = event.getLong("weight");
        allocatedBytes += weight;
        counter(allocations, getClassName(event.getClass("objectClass"))).add(weight);
    }

    private void processGarbageCollection(RecordedEvent event) {
        if (!event.hasField("name") || !event.hasField("sumOfPauses")) return;
        Duration pause = event.getDuration("sumOfPauses");
        Duration longest = event.hasField("longestPause") ? event.getDuration("longestPause") : pause;
        counter(collections, Objects.toString(event.getString("name"), UNKNOWN)).add(pause.toNanos(), longest.toNanos());
    }

    private void processContention(RecordedEvent event) {
        if (!event.hasField("monitorClass")) return;
        long duration = event.getDuration().toNanos();
        counter(contentions, getClassName(event.getClass("monitorClass"))).add(duration, duration);
    }

    private void processException(RecordedEvent event) {
        if (!event.hasField("thrownClass")) return;
        counter(exceptions, getClassName(event.getClass("thrownClass"))).add(1);
    }

    private void processExceptionStatistics(RecordedEvent event) {
        if (!event.hasField("throwables")) return;
        long throwables = event.getLong("throwables");
        minThrowables = Math.min(minThrowables, throwables);
        maxThrowables = Math.max(maxThrowables, throwables);
    }

    private void processCpuLoad(RecordedEvent event) {
        if (event.hasField("jvmUser") && event.hasField("jvmSystem")) {
            jvmCpu.add(event.getFloat("jvmUser") + event.getFloat("jvmSystem"));
        }
        if (event.hasField("machineTotal")) machineCpu.add(event.getFloat("machineTotal"));
    }

    private void processHeapSummary(RecordedEvent event) {
        if (event.hasField("heapUsed")) maxHeapUsed = Math.max(maxHeapUsed, event.getLong("heapUsed"));
    }

    private String render() {
        StringBuilder builder = new StringBuilder();
        if (eventCount == 0) return builder.append("The recording does not contain any events.").toString();
        builder.append("Recording from ").append(formatTime(startTime)).append(" to ").append(formatTime(endTime))
                .append(" (").append(formatDuration(Duration.between(startTime, endTime))).append("), ")
                .append(eventCount).append(" events.\n");
        renderResources(builder);
        renderMethods(builder);
        renderAllocations(builder);
        renderCollections(builder);
        renderContentions(builder);
        renderExceptions(builder);
        return builder.toString().stripTrailing();
    }

    private void renderResources(StringBuilder builder) {
        if (jvmCpu.isEmpty() && machineCpu.isEmpty() && maxHeapUsed < 0) return;
        builder.append('\n');
        if (!jvmCpu.isEmpty()) {
            builder.append("CPU load (JVM): average ").append(formatRatio(jvmCpu.getAverage()))
                    .append(", max ").append(formatRatio(jvmCpu.max)).append('\n');
        }
        if (!machineCpu.isEmpty()) {
            builder.append("CPU load (machine): average ").append(formatRatio(machineCpu.getAverage()))
                    .append(", max ").append(formatRatio(machineCpu.max)).append('\n');
        }
        if (maxHeapUsed >= 0) builder.append("Heap used: max ").append(formatBytes(maxHeapUsed)).append('\n');
    }

    private void renderMethods(StringBuilder builder) {
        builder.append('\n');
        if (methods.isEmpty()) {
            builder.append("No execution samples recorded (no thread was running Java code).\n");
            return;
        }
        builder.append("Hot methods (top ").append(Math.min(top, methods.size())).append(" of ").append(methods.size())
                .append(" methods, ").append(sampleCount).append(" execution samples):\n\n");
        Table table = new Table("Samples", "Percent", "Method");
        for (Map.Entry<String, Counter> entry : getTop(methods)) {
            long count = entry.getValue().total;
            table.add(Long.toString(count), formatRatio((double) count / sampleCount), entry.getKey());
        }
        table.render(builder);
    }

    private void renderAllocations(StringBuilder builder) {
        builder.append('\n');
        if (allocations.isEmpty()) {
            builder.append("No allocation samples recorded.\n");
            return;
        }
        builder.append("Allocations (top ").append(Math.min(top, allocations.size())).append(" of ")
                .append(allocations.size()).append(" classes by sampled weight, estimated total ")
                .append(formatBytes(allocatedBytes)).append("):\n\n");
        Table table = new Table("Size", "Percent", "Samples", "Class");
        for (Map.Entry<String, Counter> entry : getTop(allocations)) {
            Counter counter = entry.getValue();
            table.add(formatBytes(counter.total), formatRatio((double) counter.total / allocatedBytes),
                    Long.toString(counter.count), entry.getKey());
        }
        table.render(builder);
    }

    private void renderCollections(StringBuilder builder) {
        builder.append('\n');
        if (collections.isEmpty()) {
            builder.append("No garbage collections recorded.\n");
            return;
        }
        long count = collections.values().stream().mapToLong(counter -> counter.count).sum();
        long pauses = collections.values().stream().mapToLong(counter -> counter.total).sum();
        builder.append("Garbage collections (").append(count).append(" collections, total pause ")
                .append(formatNanos(pauses)).append("):\n\n");
        Table table = new Table("Collections", "Total pause", "Longest pause", "Collector");
        for (Map.Entry<String, Counter> entry : getTop(collections)) {
            Counter counter = entry.getValue();
            table.add(Long.toString(counter.count), formatNanos(counter.total), formatNanos(counter.max), entry.getKey());
        }
        table.render(builder);
    }

    private void renderContentions(StringBuilder builder) {
        builder.append('\n');
        if (contentions.isEmpty()) {
            builder.append("No lock contention recorded (above the recording threshold).\n");
            return;
        }
        builder.append("Lock contention (top ").append(Math.min(top, contentions.size())).append(" of ")
                .append(contentions.size()).append(" monitor classes by blocked time):\n\n");
        Table table = new Table("Events", "Total blocked", "Longest", "Monitor class");
        for (Map.Entry<String, Counter> entry : getTop(contentions)) {
            Counter counter = entry.getValue();
            table.add(Long.toString(counter.count), formatNanos(counter.total), formatNanos(counter.max), entry.getKey());
        }
        table.render(builder);
    }

    private void renderExceptions(StringBuilder builder) {
        builder.append('\n');
        if (maxThrowables >= minThrowables) {
            builder.append("Throwables created during the recording: ").append(maxThrowables - minThrowables).append('\n');
        }
        if (exceptions.isEmpty()) {
            builder.append("No exceptions or errors thrown (exception events are disabled by the recording settings).\n");
            return;
        }
        builder.append("Thrown exceptions and errors (top ").append(Math.min(top, exceptions.size())).append(" of ")
                .append(exceptions.size()).append(" classes):\n\n");
        Table table = new Table("Count", "Class");
        for (Map.Entry<String, Counter> entry : getTop(exceptions)) {
            table.add(Long.toString(entry.getValue().total), entry.getKey());
        }
        table.render(builder);
    }

    private List<Map.Entry<String, Counter>> getTop(Map<String, Counter> counters) {
        return counters.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Counter> entry) -> entry.getValue().total).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(top)
                .toList();
    }

    private static Counter counter(Map<String, Counter> counters, String key) {
        return counters.computeIfAbsent(key, k -> new Counter());
    }

    private static String getMethodName(RecordedMethod method) {
        if (method == null) return UNKNOWN;
        return getClassName(method.getType()) + "." + method.getName();
    }

    /**
     * Returns the (Java language) name of a class, converting array descriptors (like {@code [B}) to
     * {@code byte[]}.
     */
    static String getClassName(RecordedClass recordedClass) {
        if (recordedClass == null || recordedClass.getName() == null) return UNKNOWN;
        return getClassName(recordedClass.getName());
    }

    static String getClassName(String name) {
        int dimensions = 0;
        while (dimensions < name.length() && name.charAt(dimensions) == '[') dimensions++;
        if (dimensions == 0) return name;
        String element = name.substring(dimensions);
        String type = switch (element) {
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            default -> element.startsWith("L") && element.endsWith(";")
                    ? element.substring(1, element.length() - 1) : element;
        };
        return type + "[]".repeat(dimensions);
    }

    private static String formatTime(Instant instant) {
        return DATE_TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static String formatNanos(long nanos) {
        return formatDuration(Duration.ofNanos(nanos));
    }

    private static String formatRatio(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100);
    }

    /**
     * Counts events and accumulates a value (and its maximum) per key.
     */
    private static class Counter {

        private long count;
        private long total;
        private long max;

        void add(long value) {
            add(value, value);
        }

        void add(long value, long maxCandidate) {
            count++;
            total += value;
            max = Math.max(max, maxCandidate);
        }
    }

    /**
     * Average and maximum of a series of values.
     */
    private static class Statistics {

        private long count;
        private double sum;
        private double max;

        void add(double value) {
            count++;
            sum += value;
            max = Math.max(max, value);
        }

        boolean isEmpty() {
            return count == 0;
        }

        double getAverage() {
            return count == 0 ? 0 : sum / count;
        }
    }

    /**
     * A text table, with all columns right aligned except the last one (usually a name).
     */
    private static class Table {

        private final String[] headers;
        private final List<String[]> rows = new ArrayList<>();

        Table(String... headers) {
            this.headers = headers;
        }

        void add(String... values) {
            rows.add(values);
        }

        void render(StringBuilder builder) {
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) widths[i] = Math.max(widths[i], row[i].length());
            }
            renderSeparator(builder, widths);
            renderRow(builder, widths, headers);
            renderSeparator(builder, widths);
            for (String[] row : rows) renderRow(builder, widths, row);
            renderSeparator(builder, widths);
        }

        private void renderSeparator(StringBuilder builder, int[] widths) {
            builder.append('+');
            for (int width : widths) builder.append("-".repeat(width + 2)).append('+');
            builder.append('\n');
        }

        private void renderRow(StringBuilder builder, int[] widths, String[] values) {
            builder.append('|');
            for (int i = 0; i < widths.length; i++) {
                String value = values[i];
                String padding = " ".repeat(widths[i] - value.length());
                builder.append(' ');
                if (i == widths.length - 1) {
                    builder.append(value).append(padding);
                } else {
                    builder.append(padding).append(value);
                }
                builder.append(" |");
            }
            builder.append('\n');
        }
    }
}
