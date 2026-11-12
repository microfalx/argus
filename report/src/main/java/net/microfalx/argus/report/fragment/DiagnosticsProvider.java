package net.microfalx.argus.report.fragment;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.diagnostics.Diagnostics;
import net.microfalx.argus.diagnostics.DiagnosticsService;
import net.microfalx.argus.report.AbstractFragmentProvider;
import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.Report;
import net.microfalx.argus.report.Template;
import net.microfalx.lang.annotation.Provider;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

import static java.time.Duration.ofHours;
import static net.microfalx.lang.EnumUtils.toLabel;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * A fragment which displays the diagnostics collected during the report interval, grouped by type.
 */
@Slf4j
@SuppressWarnings("unused")
@Provider
public class DiagnosticsProvider extends AbstractFragmentProvider {

    /**
     * The maximum number of lines extracted from the content of each diagnostics.
     */
    private static final int MAX_CONTENT_LINES = 1000;

    private static final String NO_CONTENT = "[No content]";

    @Override
    public Fragment create() {
        return Fragment.builder("Diagnostics").template("diagnostics")
                .icon("fa-solid fa-stethoscope")
                .order(850)
                .build();
    }

    @Override
    public void update(Template template) {
        super.update(template);
        Collection<Group> groups = getGroups();
        template.addVariable("diagnosticsHelper", this);
        template.addVariable("diagnosticsGroups", groups);
        template.addVariable("diagnosticsList", getItems(groups));
        template.addVariable("diagnosticsCount", groups.stream().mapToInt(group -> group.getDiagnostics().size()).sum());
    }

    /**
     * Returns the status of a diagnostics.
     *
     * @param diagnostics the diagnostics
     * @return a non-null instance
     */
    public String getStatus(Diagnostics diagnostics) {
        if (diagnostics.getFailure() != null) return "Failed";
        if (diagnostics.getEndTime() == null) return diagnostics.getStartTime() == null ? "Pending" : "Running";
        return "Completed";
    }

    /**
     * Returns the CSS class (badge) associated with the status of a diagnostics.
     *
     * @param diagnostics the diagnostics
     * @return a non-null instance
     */
    public String getStatusCls(Diagnostics diagnostics) {
        if (diagnostics.getFailure() != null) return "badge bg-red-lt";
        if (diagnostics.getEndTime() == null) return "badge bg-blue-lt";
        return "badge bg-green-lt";
    }

    /**
     * Returns the size of the data collected by a diagnostics.
     *
     * @param diagnostics the diagnostics
     * @return a non-null instance
     */
    public String getSize(Diagnostics diagnostics) {
        if (diagnostics.getSize() >= 0) return formatBytes(diagnostics.getSize());
        return "-";
    }

    /**
     * Returns the content (as text) of a diagnostics.
     * <p>
     * If the diagnostics failed, the failure is returned instead. Only the first {@link #MAX_CONTENT_LINES} lines
     * are returned.
     *
     * @param diagnostics the diagnostics
     * @return a non-null instance
     */
    public String getContent(Diagnostics diagnostics) {
        if (diagnostics.getFailure() != null) {
            return diagnostics.getFailure().getRootCauseDescription();
        }
        if (!diagnostics.exists()) return NO_CONTENT;
        try {
            String content = diagnostics.getHead(MAX_CONTENT_LINES);
            return isEmpty(content) ? NO_CONTENT : content;
        } catch (Exception e) {
            return "Failed to extract content: " + getRootCauseDescription(e);
        }
    }

    private static List<Item> getItems(Collection<Group> groups) {
        return groups.stream()
                .flatMap(group -> group.getDiagnostics().stream().map(diagnostics -> new Item(group.getName(), diagnostics)))
                .sorted(Comparator.comparing((Item item) -> item.getDiagnostics().getStartTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Collection<Group> getGroups() {
        DiagnosticsService diagnosticsService;
        try {
            diagnosticsService = DiagnosticsService.getInstance();
        } catch (Exception e) {
            LOGGER.debug("Diagnostics service is not available: {}", getRootCauseDescription(e));
            return Collections.emptyList();
        }
        ZonedDateTime end = ZonedDateTime.now();
        ZonedDateTime start = end.minus(ofHours(24));
        try {
            Report report = Report.current();
            if (report.getEndTime() != null) end = report.getEndTime();
            if (report.getStartTime() != null) start = report.getStartTime();
        } catch (IllegalStateException e) {
            // no report available, use the default interval
        }
        LocalDateTime startLocal = start.toLocalDateTime();
        LocalDateTime endLocal = end.toLocalDateTime();
        List<Group> groups = new ArrayList<>();
        for (Diagnostics.Type type : Diagnostics.Type.values()) {
            try {
                List<Diagnostics> diagnostics = new ArrayList<>(diagnosticsService.getDiagnostics(type, startLocal, endLocal));
                if (diagnostics.isEmpty()) continue;
                diagnostics.sort(Comparator.comparing(Diagnostics::getStartTime,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                groups.add(new Group(type, diagnostics));
            } catch (Exception e) {
                LOGGER.atWarn().setCause(e).log("Failed to extract diagnostics of type {}", type);
            }
        }
        return groups;
    }

    /**
     * Holds all diagnostics of a given type.
     */
    public static final class Group {

        private final Diagnostics.Type type;
        private final List<Diagnostics> diagnostics;

        private Group(Diagnostics.Type type, List<Diagnostics> diagnostics) {
            this.type = type;
            this.diagnostics = Collections.unmodifiableList(diagnostics);
        }

        public String getId() {
            return type.name().toLowerCase();
        }

        public String getName() {
            return toLabel(type);
        }

        public List<Diagnostics> getDiagnostics() {
            return diagnostics;
        }
    }

    /**
     * Holds a diagnostics and the label of its type (the type is not accessible outside the diagnostics package).
     */
    public static final class Item {

        private final String type;
        private final Diagnostics diagnostics;

        private Item(String type, Diagnostics diagnostics) {
            this.type = type;
            this.diagnostics = diagnostics;
        }

        public String getType() {
            return type;
        }

        public Diagnostics getDiagnostics() {
            return diagnostics;
        }
    }
}
