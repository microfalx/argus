package net.microfalx.argus.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.*;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;

import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.CollectionUtils.immutableCollection;
import static net.microfalx.lang.CollectionUtils.immutableSet;
import static net.microfalx.lang.FormatterUtils.formatNumber;
import static net.microfalx.lang.StringUtils.EMPTY_STRING;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * A rating assigned to a resource to indicate the health of that resource.
 * <p>
 * The outcome of the assessment is represented by a score is a value between 1 and 5, 1 being completely "faulty" and 5 being completely "healthy". The score is
 * calculated based on the thresholds defined for various internal services/metrics.
 * <p>
 * The score is linearly extrapolated by applying the following algorithm:
 * <ul>
 * <li>metrics below warning move the score towards 5</li>
 * <li>metrics above error move the score towards 1</li>
 * <li>metrics between warning and error map to a value between 2 and 4</li>
 * </ul>
 * <p>
 * <pre>
 * Score:        5 -------- 4 -------- 2 -------- 1
 * Thresholds:           warning     error
 * Areas:       healthy    with issues   faulty
 * </pre>
 */
@Getter
@ToString(callSuper = true)
@net.microfalx.lang.annotation.Version
public final class Health extends IdentityAware<String> implements Timestampable<ZonedDateTime> {

    @Serial private static final long serialVersionUID = 8458921058638887336L;

    public static final float NA = 0;
    public static final float MIN = 1;
    public static final float MAX = 10;
    public static final float WARNING = 7;
    public static final float ERROR = 4;
    public static final float BAD = 2f;
    public static final float WITH_ISSUES = 6.5f;

    private static final int DEFAULT_WORSE_ITEMS = 3;

    /**
     * Holds the timestamp when this health score was created. The timestamp is set when the instance
     * is created and cannot be modified.
     */
    private final ZonedDateTime createdAt = ZonedDateTime.now();

    /**
     * Holds the timestamp when this health score was last modified. The timestamp is updated whenever
     * the score is modified.
     */
    private ZonedDateTime modifiedAt = createdAt;

    /**
     * Indicates whether this health score is read-only. If {@code true}, the score cannot be modified.
     */
    private boolean readOnly;

    /**
     * Holds the trend of the scores for this health score and its groups.
     */
    @JsonIgnore
    private TrendStatisticalSummary trend;

    /**
     * The type of the health score, which indicates whether the score is calculated for the entire site,
     * a specific resource, or a specific instance of a resource.
     */
    private Type type = Type.INSTANCE;

    private final Map<String, Group> groups = new LinkedHashMap<>();

    public Health() {
        setId(IdGenerator.get().nextAsString());
    }

    public Health(String id) {
        setId(id);
    }

    /**
     * Returns the summary of the scores for this group and its subgroups.
     *
     * @return a non-null instance
     */
    public TrendStatisticalSummary getTrend() {
        if (trend == null) {
            trend = TrendStatisticalSummary.create();
            ((MutableStatisticalSummary) trend).add(getScore());
        }
        return trend;
    }

    /**
     * Attaches a new summary to this group.
     *
     * @param trend the summary to attach
     */
    public void setTrend(TrendStatisticalSummary trend) {
        requireNonNull(trend);
        this.trend = trend;
    }

    /**
     * Returns all scored objects, which includes all groups and items.
     *
     * @return a non-null instance
     */
    public Collection<Scored> getScored() {
        Collection<Scored> scored = new ArrayList<>();
        for (Group value : groups.values()) {
            updateScored(scored, value);
        }
        return scored;
    }

    /**
     * Returns the groups part of this score.
     *
     * @return a non-null instance
     */
    public Collection<Group> getGroups() {
        return immutableCollection(groups.values());
    }

    /**
     * Returns the groups part of this score.
     *
     * @param all if {@code true}, returns all groups from this score and its subgroups,
     *            otherwise returns only the groups of this score
     * @return a non-null instance
     */
    public Collection<Group> getGroups(boolean all) {
        if (all) {
            Collection<Group> allGroups = new ArrayList<>();
            for (Group group : groups.values()) {
                group.addGroups(allGroups);
            }
            return allGroups;
        } else {
            return getGroups();
        }
    }

    /**
     * Returns a group by its name.
     *
     * @param name the name of the group
     * @return a non-null instance
     */
    public Group getGroup(String name) {
        requireNotEmpty(name);
        return groups.computeIfAbsent(toIdentifier(name), s -> {
            checkReadOnly();
            Group group = new Group(name);
            group.setParent(this);
            group.order = groups.size() * 10;
            group.readOnly = readOnly;
            return group;
        });
    }

    /**
     * Returns the final score, which is the score of the item with the lowest score.
     *
     * @return a value between 1 and 5
     */
    public float getScore() {
        return getLowest().map(Item::getScore).orElse(MAX);
    }

    /**
     * Returns the severity of the score.
     *
     * @return a non-null instance
     */
    public Severity getSeverity() {
        return toSeverity(getScore());
    }

    /**
     * Returns the item with the lowest score across all groups.
     *
     * @return an optional item
     */
    public Optional<Item> getLowest() {
        return groups.values().stream()
                .flatMap(group -> group.getItems(true).stream().filter(item -> item.getScore() != NA))
                .min(Comparator.comparing(Item::getScore));
    }

    /**
     * Returns the items with the lowest scores across all groups, limited to a default maximum number of items.
     *
     * @return a non-null instance
     */
    public Collection<Health.Item> getWorstItems() {
        return getWorstItems(DEFAULT_WORSE_ITEMS);
    }

    /**
     * Returns the items with the lowest scores across all groups, limited to a maximum number of items.
     *
     * @param maxItems the maximum number of items to return
     * @return a non-null instance
     * @see Item#hasIssues()
     */
    public Collection<Health.Item> getWorstItems(int maxItems) {
        return groups.values().stream().flatMap(group -> group.getItems(true).stream())
                .filter(Health.Item::hasIssues)
                .sorted(Comparator.comparing(Health.Item::getScore))
                .limit(maxItems)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Registers an item with its score.
     *
     * @param group the name of the group which owns the item
     * @param item  the name of the item
     * @param score the score of the item
     */
    public Item update(String group, String item, float score) {
        requireNotEmpty(group);
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        return getGroup(group).update(item, score);
    }

    /**
     * Registers an item with its score.
     * <p>
     * The registration ignores the possible group of the item and uses the provided group instead.
     *
     * @param group the name of the group which owns the item
     * @param item  the item
     */
    public Item update(String group, Item item) {
        requireNotEmpty(group);
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        return getGroup(group).update(item);
    }

    /**
     * Registers an item with its score.
     * <p>
     * The group is picked from
     *
     * @param item the item
     */
    public Item update(Item item) {
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        String group = item.getGroup().orElse("General");
        return getGroup(group).update(item);
    }

    /**
     * Generates the health report, highlighting the group with the lowest score and the item  which gives the group
     * the lowest score out of all groups.
     * <p>
     * The report is rendered as plain text.
     *
     * @return a non-null instance
     * @see #getReport(RichText.Format)
     */
    public String getReport() {
        return getReport(RichText.Format.TEXT);
    }

    /**
     * Generates the health report, highlighting the group with the lowest score and the item  which gives the group
     * the lowest score out of all groups.
     * <p>
     * With {@link RichText.Format#ANSI} or {@link RichText.Format#HTML}, the score of each item  is colored
     * based on its {@link Severity severity}; with {@link RichText.Format#TEXT}, the report  is plain text.
     *
     * @param format the format of the report
     * @return a non-null instance
     */
    public String getReport(RichText.Format format) {
        requireNonNull(format);
        Report report = new Report(format);
        Item lowestItem = getLowest().orElse(null);
        Group lowestGroup = lowestItem != null ? groups.values().stream()
                .filter(g -> g.getItems().contains(lowestItem))
                .findFirst().orElse(null) : null;
        groups.values().stream()
                .sorted(Comparator.comparingInt(g -> g.order))
                .forEach(group -> group.report(report, lowestItem, group == lowestGroup, 1));
        return report.render();
    }

    /**
     * Generates a summary report with the worst items, limited to a default maximum number of items.
     *
     * @param format the format of the report
     * @return a non-null instance
     * @see #getWorstItemsReport(int, RichText.Format)
     */
    public String getWorstItemsReport(RichText.Format format) {
        return getWorstItemsReport(DEFAULT_WORSE_ITEMS, format);
    }

    /**
     * Generates a summary report with the worst items, intended for tooltips or as a summary at the bottom of
     * the {@link #getReport(RichText.Format) full report}.
     * <p>
     * Each item is rendered on its own line, prefixed with a status circle colored based on its
     * {@link Severity severity}, followed by the item rendered as in the full report, with its name prefixed
     * by the group hierarchy ({@code Group/Subgroup/Item: score (description)}).
     *
     * @param maxItems the maximum number of items to include
     * @param format   the format of the report
     * @return a non-null instance, empty if there are no items with issues
     * @see #getWorstItems(int)
     */
    public String getWorstItemsReport(int maxItems, RichText.Format format) {
        requireNonNull(format);
        Collection<Item> worstItems = getWorstItems(maxItems);
        Map<Item, String> paths = new IdentityHashMap<>();
        for (Group group : groups.values()) {
            collectPaths(paths, group, group.getName());
        }
        Report report = new Report(format);
        for (Item item : worstItems) {
            report.line(RichText.create().glyph(item.getSeverity().getGlyph())
                    .a(item.toRichText(paths.get(item))));
        }
        return report.render();
    }

    private static void collectPaths(Map<Item, String> paths, Group group, String path) {
        for (Item item : group.getItems()) {
            paths.put(item, path);
        }
        for (Group childGroup : group.getGroups()) {
            collectPaths(paths, childGroup, path + "/" + childGroup.getName());
        }
    }

    /**
     * Changes the type of this health score.
     *
     * @param type a non-null instance
     */
    public void setType(Type type) {
        requireNonNull(type);
        checkReadOnly();
        this.type = type;
    }

    /**
     * Creates a read-only copy of this health instance.
     *
     * @return a non-null instance
     */
    public Health readOnly() {
        Health copy = (Health) copy();
        copy.readOnly = true;
        for (Group group : copy.groups.values()) {
            group.readOnly();
        }
        return copy;
    }

    /**
     * Normalizes the score value to be within its bounds.
     *
     * @param value the value
     * @return the normalized value
     */
    public static float normalize(float value) {
        if (value == NA) return NA;
        return Math.max(Health.MIN, Math.min(Health.MAX, value));
    }

    /**
     * Converts a score to a severity level.
     *
     * @param score the score
     * @return a non-null instance
     */
    public static Severity toSeverity(float score) {
        if (score == NA) return Severity.NA;
        if (score >= WARNING) {
            return Severity.OK;
        } else if (score >= WITH_ISSUES) {
            return Severity.LOW;
        } else if (score >= ERROR) {
            return Severity.MEDIUM;
        } else if (score >= BAD) {
            return Severity.HIGH;
        } else {
            return Severity.CRITICAL;
        }
    }

    /**
     * Converts a score to a rich text, with the score colored based on its severity.
     *
     * @param score the score
     * @return a non-null instance
     */
    public static RichText scoreToRichText(float score) {
        Severity severity = toSeverity(score);
        RichText text = RichText.create().fg(toColor(severity));
        if (severity == Severity.CRITICAL) text.bold();
        return text.a(formatNumber(score, 1));
    }

    /**
     * Converts a severity to a color.
     *
     * @param severity the severity
     * @return a non-null instance
     */
    public static RichText.Color toColor(Severity severity) {
        return switch (severity) {
            case NA -> RichText.Color.GRAY;
            case OK -> RichText.Color.GREEN;
            case LOW -> RichText.Color.YELLOW;
            case MEDIUM -> RichText.Color.BRIGHT_RED;
            case HIGH, CRITICAL -> RichText.Color.RED;
        };
    }

    private void updateScored(Collection<Scored> scored, Group group) {
        scored.add(group);
        for (Group childGroup : group.getGroups()) {
            updateScored(scored, childGroup);
        }
        scored.addAll(group.getItems());
    }

    private void checkReadOnly() {
        if (readOnly) {
            throw new IllegalStateException("Health is read-only");
        }
    }

    /**
     * An interface for an object which provides a score.
     */
    public interface Scored extends Identifiable<String>, Nameable {

        /**
         * Returns the score of the object.
         *
         * @return the score
         */
        float getScore();
    }

    /**
     * A severity associated with the issue.
     */
    public enum Severity {

        /**
         * No severity can be determined - no impact
         */
        NA,

        /**
         * No severity issue - no impact
         */
        OK,

        /**
         * Low severity issue - minor impact
         */
        LOW,

        /**
         * Medium severity issue - moderate impact
         */
        MEDIUM,

        /**
         * High severity issue - significant impact
         */
        HIGH,

        /**
         * Critical severity issue - severe impact
         */
        CRITICAL;

        /**
         * Returns the severity as a glyph for display purposes.
         *
         * @return a non-null instance
         */
        public String getGlyph() {
            return switch (this) {
                case NA -> Glyph.WHITE_CIRCLE;
                case LOW -> Glyph.BLUE_CIRCLE;
                case OK -> Glyph.GREEN_CIRCLE;
                case MEDIUM -> Glyph.YELLOW_CIRCLE;
                case HIGH -> Glyph.ORANGE_CIRCLE;
                case CRITICAL -> Glyph.RED_CIRCLE;
            };
        }
    }

    /**
     * An enum which provides the type of the health score.
     */
    public enum Type {

        /**
         * The score is calculated for the entire site, which includes all resources supporting the site
         * (services, servers, etc).
         */
        SITE,

        /**
         * The score is calculated for a specific resource, which may include multiple instances
         * (a service, a server cluster, etc).
         */
        RESOURCE,

        /**
         * The score is calculated for a specific instance of a resource
         * (service replica, single server, etc).
         */
        INSTANCE
    }

    @Getter
    @ToString
    @net.microfalx.lang.annotation.Version
    public static class Group extends NamedIdentityAware<String> implements Scored {

        @Serial
        private static final long serialVersionUID = 5737407091915538304L;

        private final Collection<Item> items = new ArrayList<>();
        private final Map<String, Group> groups = new LinkedHashMap<>();

        /**
         * Holds the parent of this group, which is either another group or the health score itself.
         */
        private String group;

        /**
         * Holds the trend of the scores for this group and its subgroups.
         */
        private TrendStatisticalSummary trend;

        /**
         * Holds the order of the group within the score.
         */
        private int order;

        /**
         * Holds the depth of the group within the score. The root group has a depth of 1,
         * its subgroups have a depth of 2, and so on.
         */
        private int depth = 1;

        /**
         * Indicates whether the group is read-only. If {@code true}, the group cannot be modified.
         */
        private boolean readOnly;

        /**
         * Creates a group.
         *
         * @param name the name of the group
         * @return a non-null instance
         */
        public static Group create(String name) {
            return new Group(name);
        }

        private Group(String name) {
            requireNotEmpty(name);
            this.setId(toIdentifier(name));
            this.setName(name);
        }

        /**
         * Returns the subgroups of this group.
         *
         * @return a non-null instance
         */
        public Collection<Group> getGroups() {
            return immutableCollection(groups.values());
        }

        /**
         * Returns a subgroup by its name.
         *
         * @param name the name of the subgroup
         * @return a non-null instance
         */
        public Group getGroup(String name) {
            requireNotEmpty(name);
            return groups.computeIfAbsent(toIdentifier(name), s -> {
                checkReadOnly();
                Group group = new Group(name);
                group.setParent(this);
                group.order = groups.size() * 10;
                group.readOnly = readOnly;
                group.depth = depth + 1;
                group.setParent(this);
                return group;
            });
        }

        /**
         * Returns the  items of this group.
         *
         * @return a non-null instance
         */
        public Collection<Item> getItems() {
            return immutableCollection(items);
        }

        /**
         * Returns the items of this group and all its subgroups.
         *
         * @param all if {@code true}, returns all items from this group and its subgroups,
         *            otherwise returns only the items of this group
         * @return a non-null instance
         */
        public Collection<Item> getItems(boolean all) {
            if (all) {
                Collection<Item> allItems = new ArrayList<>(items);
                groups.values().forEach(group -> allItems.addAll(group.getItems(true)));
                allItems.addAll(items);
                return immutableCollection(allItems);
            } else {
                return getItems();
            }
        }

        /**
         * Returns the score for this group, which is the score of the item with
         * the lowest score.
         *
         * @return a value between 1 and 5
         */
        public float getScore() {
            return getLowest().map(Item::getScore).orElse(5f);
        }

        /**
         * Returns the item with the lowest score in this group or any subgroup.
         *
         * @return an optional item
         */
        public Optional<Item> getLowest() {
            Optional<Group> minGroup = groups.values().stream().min(Comparator.comparing(Group::getScore));
            Optional<Item> minItem = items.stream().filter(item -> item.getScore() != NA).min(Comparator.comparing(Item::getScore));
            return Stream.of(minGroup.flatMap(Group::getLowest), minItem)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .min(Comparator.comparing(Item::getScore));
        }

        /**
         * Registers an item with its score and a {@link Policy} of {@link Policy#REPORT_IF_DOWNGRADE}..
         *
         * @param name  the name of the item
         * @param score the score of the item
         */
        public Item update(String name, float score) {
            Item item = new Item(name, score);
            item.setParent(this);
            checkReadOnly();
            items.add(item);
            return item;
        }

        /**
         * Registers an item with its score.
         *
         * @param item the item
         */
        public Item update(Item item) {
            requireNonNull(item);
            checkReadOnly();
            item.setParent(this);
            items.add(item);
            return item;
        }

        /**
         * Returns the summary of the scores for this group and its subgroups.
         *
         * @return a non-null instance
         */
        public TrendStatisticalSummary getTrend() {
            if (trend == null) {
                trend = TrendStatisticalSummary.create();
                ((MutableStatisticalSummary) trend).add(getScore());
            }
            return trend;
        }

        /**
         * Attaches a new summary to this group.
         *
         * @param trend the trend to attach
         */
        public void setTrend(TrendStatisticalSummary trend) {
            requireNonNull(trend);
            this.trend = trend;
        }

        /**
         * Writes a report for this group to the provided report.
         * <p>
         * The group header is prefixed with {@code *} if {@code isLowest} is {@code true}.
         * Each item is indented and prefixed with {@code *} if it is the globally lowest item.
         *
         * @param report     the report to write to
         * @param lowestItem the item with the lowest score across all groups (may be {@code null})
         * @param isLowest   {@code true} if this group contains the globally lowest item
         */
        void report(Report report, Item lowestItem, boolean isLowest, int depth) {
            String lowestSuffix = isLowest ? " (*)" : EMPTY_STRING;
            String finalName = getName() + lowestSuffix;
            if (depth > 1) {
                report.line(RichText.create().bullet().a(finalName));
            } else {
                report.line(RichText.of(finalName));
            }
            report.increaseIndent();
            groups.values().stream()
                    .sorted(Comparator.comparingInt(g -> g.order))
                    .forEach(group -> {
                        group.report(report, lowestItem, isLowest, depth + 1);
                    });
            for (Item item : items) {
                if (!item.shouldReport()) continue;
                boolean isLowestItem = item == lowestItem;
                RichText line = RichText.create();
                if (depth > 1) {
                    line.square();
                } else {
                    line.bullet();
                }
                line.a(item.toRichText());
                if (isLowestItem) line.a(lowestSuffix);
                report.line(line);
            }
            report.decreaseIndent();
        }

        private void addGroups(Collection<Group> groups) {
            groups.add(this);
            for (Group group : this.groups.values()) {
                group.addGroups(groups);
            }
        }

        private void setParent(Identifiable<?> parent) {
            if (parent instanceof Nameable nameable) this.group = nameable.getName();
            this.setId(parent.getId() + "." + toIdentifier(getName()));
        }

        private void readOnly() {
            readOnly = true;
            for (Group group : groups.values()) {
                group.readOnly();
            }
        }

        private void checkReadOnly() {
            if (readOnly) {
                throw new IllegalStateException("Health is read-only");
            }
        }
    }

    @Getter
    @ToString
    @net.microfalx.lang.annotation.Version
    public static class Item extends NamedIdentityAware<String> implements Scored {

        @Serial
        private static final long serialVersionUID = 3745495489398322801L;

        /**
         * The group to which this item belongs
         */
        private String group;

        /**
         * The score of the item
         */
        private final float score;

        /**
         * The value used to calculate the score. This value is used to determine the score based on thresholds.
         */
        private final float value;

        /**
         * Holds the trend of the scores for this group and its subgroups.
         */
        private TrendStatisticalSummary trend;

        /**
         * The policy associated with the item, which determines how it is reported in the score.
         */
        private Set<Policy> policies = EMPTY_POLICIES;

        /**
         * The thresholds used to create this item
         */
        private Thresholds thresholds;

        /**
         * Creates new item with a given name and score.
         *
         * @param name  the item name
         * @param score the item score
         * @return a new item instance
         */
        public static Item create(String name, float score) {
            return new Item(name, score);
        }

        /**
         * Creates new item from a value and thresholds.
         * <p>
         * This method creates an item with a score based on an absolute value.
         *
         * @param thresholds the thresholds used to score the value
         * @param value      the item value
         * @return a new item instance
         */
        public static Item create(Thresholds thresholds, float value) {
            requireNonNull(thresholds);
            float score = thresholds.getScore(value);
            return new Item(thresholds.getName(), score, value).withThresholds(thresholds);
        }

        /**
         * Creates new item from a value and thresholds.
         * <p>
         * This method creates an item with a score based on the value relative to thresholds and updates the description
         * with the value explained in the description
         *
         * @param thresholds the thresholds used to score the value
         * @param value      the item score
         * @return a new item instance
         */
        public static Item create(Thresholds thresholds, float value, Unit unit) {
            requireNonNull(thresholds);
            requireNonNull(unit);
            float score = thresholds.getScore(value);
            String description = String.format("%s", unit.format(value));
            return new Item(thresholds.getName(), score, value).withDescription(description)
                    .withThresholds(thresholds);
        }

        /**
         * Creates new item from a value relative to a maximum and thresholds.
         * <p>
         * This method creates an item with a score based on a percentage and updates the description with the
         * value & max & percentage explained in the description
         *
         * @param thresholds the thresholds used to score the value
         * @param value      the item score
         * @return a new item instance
         */
        public static Item create(Thresholds thresholds, float value, float maximum, Unit unit) {
            requireNonNull(thresholds);
            requireNonNull(unit);
            if (thresholds.getUnit() != Unit.PERCENT) {
                throw new IllegalArgumentException("Thresholds must have a unit of PERCENT");
            }
            float percent = NumberUtils.percent(value, maximum);
            float score = thresholds.getScore(percent);
            String description = String.format("%s [%s of %s]", Unit.PERCENT.format(percent),
                    unit.format(value), unit.format(maximum));
            return new Item(thresholds.getName(), score, value).withDescription(description)
                    .withThresholds(thresholds);
        }

        private Item(String name, float score) {
            this(name, score, Float.MIN_VALUE);
        }

        private Item(String name, float score, float value) {
            requireNotEmpty(name);
            this.setId(toIdentifier(name));
            this.setName(name);
            this.score = Health.normalize(score);
            this.value = value;
        }

        /**
         * Returns whether the item has issues, which is determined by its score being below the maximum threshold.
         *
         * @return {@code true} if the item has issues, {@code false} otherwise
         */
        public boolean hasIssues() {
            return getScore() != Health.NA && getScore() < Health.WARNING;
        }

        /**
         * Returns the severity of the item score.
         *
         * @return a non-null instance
         */
        public Severity getSeverity() {
            return toSeverity(getScore());
        }

        /**
         * Renders the item as {@code name: score (description)}, with the score colored based on its severity.
         *
         * @return a non-null instance
         */
        public RichText toRichText() {
            return toRichText(null);
        }

        /**
         * Renders the item as {@code path/name: score (description)}, with the score colored based on its severity.
         *
         * @param path the path (group hierarchy separated by {@code /}) prefixed to the name, can be {@code null}
         * @return a non-null instance
         */
        public RichText toRichText(String path) {
            String name = StringUtils.isNotEmpty(path) ? path + "/" + getName() : getName();
            RichText text = RichText.create().a(name).a(": ").a(scoreToRichText(getScore()));
            String description = getDescription();
            if (description != null && !description.isEmpty()) text.a(" (").a(description).a(")");
            return text;
        }

        /**
         * Returns whether the item has a given policy.
         *
         * @param policy the policy to check
         * @return {@code true} if policy is present, {@code false} otherwise
         */
        public boolean has(Policy policy) {
            requireNonNull(policy);
            return policies.contains(policy);
        }

        /**
         * Returns the group to which this item belongs.
         *
         * @return a non-null instance
         */
        public Optional<String> getGroup() {
            if (StringUtils.isNotEmpty(group)) {
                return Optional.of(group);
            } else if (thresholds != null) {
                return thresholds.getGroup();
            } else {
                return Optional.empty();
            }
        }

        /**
         * Returns all registered policies.
         *
         * @return an immutable set
         */
        public Set<Policy> getPolicies() {
            return immutableSet(policies);
        }

        /**
         * Creates a new item with a different policy.
         *
         * @param policy the policy
         * @return a new instance
         */
        public Item withPolicy(Policy policy) {
            requireNonNull(policy);
            Item copy = (Item) copy();
            copy.policies.add(policy);
            return copy;
        }

        /**
         * Creates a new item with a different thresholds.
         *
         * @param thresholds the thresholds
         * @return a new instance
         */
        public Item withThresholds(Thresholds thresholds) {
            requireNonNull(thresholds);
            Item copy = (Item) copy();
            copy.thresholds = thresholds;
            return copy;
        }

        @Override
        public Item withName(String name) {
            return (Item) super.withName(name);
        }

        @Override
        public Item withDescription(String description) {
            return (Item) super.withDescription(description);
        }

        /**
         * Changes the group name of the thresholds.
         *
         * @param group the group name
         * @return a new instance of {@link Thresholds} with the specified reverse setting
         */
        public Item withGroup(String group) {
            requireNonNull(group);
            Item copy = (Item) copy();
            copy.group = group;
            return copy;
        }

        /**
         * Returns the summary of the scores for this item.
         *
         * @return a non-null instance
         */
        public TrendStatisticalSummary getTrend() {
            if (trend == null) {
                trend = TrendStatisticalSummary.create();
                ((MutableStatisticalSummary) trend).add(getScore());
            }
            return trend;
        }

        /**
         * Attaches a new summary to this item.
         *
         * @param trend the trend to attach
         */
        public void setTrend(TrendStatisticalSummary trend) {
            requireNonNull(trend);
            this.trend = trend;
        }

        /**
         * Returns whether the item should be reported based on its score and the registered policies.
         *
         * @return {@code true} if the item should be reported, {@code false} otherwise
         */
        public boolean shouldReport() {
            if (policies.isEmpty()) return true;
            float score = getScore();
            if (policies.contains(Policy.REPORT_IF_WARNING) && score < Health.WARNING) return true;
            if (policies.contains(Policy.REPORT_IF_ERROR) && score < Health.ERROR) return true;
            if (policies.contains(Policy.REPORT_IF_NEEDED) && score < Health.WARNING) return true;
            if (policies.contains(Policy.REPORT_IF_HAS_VALUE) && value != 0) return true;
            return false;
        }

        @Override
        protected void copyProperties(IdentityAware<String> source, IdentityAware<String> target) {
            super.copyProperties(source, target);
            ((Item) target).thresholds = thresholds;
            ((Item) target).policies = EnumSet.copyOf(policies);
        }

        private void setParent(Group parent) {
            this.group = parent.getName();
            this.setId(parent.getId() + "." + toIdentifier(getName()));
        }
    }

    /**
     * An enum which provides a policy on how the individual items are reported or handled.
     */
    public enum Policy {
        REPORT_IF_DOWNGRADE,
        REPORT_IF_WARNING,
        REPORT_IF_ERROR,
        REPORT_IF_NEEDED,
        REPORT_IF_HAS_VALUE,
        RESTART_IF_NEEDED
    }

    private static final Set<Policy> EMPTY_POLICIES = EnumSet.noneOf(Policy.class);

    /**
     * Collects the (indented) lines of a health report and renders them in a given format.
     */
    private static final class Report {

        private static final int INDENT_STEP = 2;

        private final RichText.Format format;
        private final List<RichText> lines = new ArrayList<>();
        private int indent;

        private Report(RichText.Format format) {
            this.format = format;
        }

        void increaseIndent() {
            indent += INDENT_STEP;
        }

        void decreaseIndent() {
            indent = Math.max(0, indent - INDENT_STEP);
        }

        void line(RichText text) {
            // HTML collapses regular spaces, so indent with non-breaking spaces
            char space = format == RichText.Format.HTML ? ' ' : ' ';
            lines.add(RichText.of(String.valueOf(space).repeat(indent)).a(text));
        }

        String render() {
            String separator = format == RichText.Format.HTML ? "<br>" : "\n";
            return lines.stream().map(line -> line.render(format)).collect(Collectors.joining(separator));
        }
    }
}
