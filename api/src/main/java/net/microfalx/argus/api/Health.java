package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;
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
public final class Health extends IdentityAware<Long> implements Timestampable<ZonedDateTime>, Serializable {

    @Serial private static final long serialVersionUID = 8458921058638887336L;

    public static final float MIN = 1;
    public static final float MAX = 5;
    public static final float WARNING = 4;
    public static final float ERROR = 2;
    public static final float BAD = 1.5f;
    public static final float WITH_ISSUES = 2.5f;

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
     * The type of the health score, which indicates whether the score is calculated for the entire site,
     * a specific resource, or a specific instance of a resource.
     */
    private Type type = Type.INSTANCE;
    private final Map<String, Group> groups = new LinkedHashMap<>();

    public Health() {
        setId(IdGenerator.get().next());
    }

    /**
     * Normalizes the score value to be within its bounds.
     *
     * @param value the value
     * @return the normalized value
     */
    public static float normalize(float value) {
        return Math.max(Health.MIN, Math.min(Health.MAX, value));
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
        return getLowest().map(Item::getScore).orElse(5f);
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
        return groups.values().stream().flatMap(group -> group.getItems(true).stream())
                .min(Comparator.comparing(Item::getScore));
    }

    /**
     * Registers an item with its score.
     *
     * @param group the name of the group which owns the item
     * @param item  the name of the item
     * @param score the score of the item
     */
    public void update(String group, String item, float score) {
        requireNotEmpty(group);
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        getGroup(group).update(item, score);
    }

    /**
     * Registers an item with its score.
     * <p>
     * The registration ignores the possible group of the item and uses the provided group instead.
     *
     * @param group the name of the group which owns the item
     * @param item  the item
     */
    public void update(String group, Item item) {
        requireNotEmpty(group);
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        getGroup(group).update(item);
    }

    /**
     * Registers an item with its score.
     * <p>
     * The group is picked from
     *
     * @param item the item
     */
    public void update(Item item) {
        requireNotEmpty(item);
        checkReadOnly();
        modifiedAt = ZonedDateTime.now();
        String group = item.getGroup().orElse("General");
        getGroup(group).update(item);
    }

    /**
     * Generates the health report, highlighting the group with the lowest score and the item which gives the group
     * the lowest score out of all groups.
     *
     * @return a non-null instance
     */
    public String getReport() {
        Logger logger = Logger.create();
        Item lowestItem = getLowest().orElse(null);
        Group lowestGroup = lowestItem != null ? groups.values().stream()
                .filter(g -> g.getItems().contains(lowestItem))
                .findFirst().orElse(null) : null;
        groups.values().stream()
                .sorted(Comparator.comparingInt(g -> g.order))
                .forEach(group -> group.report(logger, lowestItem, group == lowestGroup, 1));
        return logger.getOutput();
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

    public static Severity toSeverity(float score) {
        if (score >= WARNING) {
            return Severity.NONE;
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

    private void checkReadOnly() {
        if (readOnly) {
            throw new IllegalStateException("Health is read-only");
        }
    }

    /**
     * A severity associated with the issue.
     */
    public enum Severity {

        NONE,

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
        CRITICAL
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
    public static class Group implements Identifiable<String>, Nameable, Serializable {

        @Serial
        private static final long serialVersionUID = 5737407091915538304L;

        private String id;
        private final String name;
        private final Collection<Item> items = new ArrayList<>();
        private final Map<String, Group> groups = new LinkedHashMap<>();

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
            this.id = toIdentifier(name);
            this.name = name;
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
            Optional<Item> minItem = items.stream().min(Comparator.comparing(Item::getScore));
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
        public void update(String name, float score) {
            Item item = new Item(name, score);
            checkReadOnly();
            items.add(item);
        }

        /**
         * Registers an item with its score.
         *
         * @param item the item
         */
        public void update(Item item) {
            requireNonNull(item);
            checkReadOnly();
            items.add(item);
        }

        /**
         * Writes a report for this group to the provided logger.
         * <p>
         * The group header is prefixed with {@code *} if {@code isLowest} is {@code true}.
         * Each item is indented and prefixed with {@code *} if it is the globally lowest item.
         *
         * @param logger     the logger to write to
         * @param lowestItem the item with the lowest score across all groups (may be {@code null})
         * @param isLowest   {@code true} if this group contains the globally lowest item
         */
        void report(Logger logger, Item lowestItem, boolean isLowest, int depth) {
            String lowestSuffix = isLowest ? " (*)" : EMPTY_STRING;
            String finalName = name + lowestSuffix;
            if (depth > 1) {
                logger.atInfo().bullet().append(finalName).log();
            } else {
                logger.info(finalName);
            }
            logger.increaseIndent();
            groups.values().stream()
                    .sorted(Comparator.comparingInt(g -> g.order))
                    .forEach(group -> {
                        group.report(logger, lowestItem, isLowest, depth + 1);
                    });
            items.forEach(item -> {
                boolean isLowestItem = item == lowestItem;
                String description = item.getDescription();
                String line;
                String scoreAsString = formatNumber(item.getScore(), 1);
                if (description != null && !description.isEmpty()) {
                    line = String.format("%s: %s (%s)", item.getName(), scoreAsString, description);
                } else {
                    line = String.format("%s: %s", item.getName(), scoreAsString);
                }
                Logger.Entry bullet;
                if (depth > 1) {
                    bullet = logger.atInfo().square();
                } else {
                    bullet = logger.atInfo().bullet();
                }
                bullet.append(line).append(isLowestItem ? lowestSuffix : EMPTY_STRING).log();
            });
            logger.decreaseIndent();
        }

        private void addGroups(Collection<Group> groups) {
            groups.add(this);
            for (Group group : this.groups.values()) {
                group.addGroups(groups);
            }
        }

        private void setParent(Group parent) {
            this.id = parent.getId() + "." + toIdentifier(name);
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
    public static class Item extends NamedIdentityAware<String> implements Serializable {

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
            return new Item(thresholds.getName(), score).withThresholds(thresholds);
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
            return new Item(thresholds.getName(), score).withDescription(description)
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
            return new Item(thresholds.getName(), score).withDescription(description)
                    .withThresholds(thresholds);
        }

        private Item(String name, float score) {
            requireNotEmpty(name);
            this.setId(toIdentifier(name));
            this.setName(name);
            this.score = Health.normalize(score);
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
         * Creates a new item with a different name.
         *
         * @param name the name
         * @return a new instance
         */
        public Item withName(String name) {
            Item copy = (Item) copy();
            copy.setName(name);
            return copy;
        }

        /**
         * Creates a new item with a different description.
         *
         * @param description the description
         * @return a new instance
         */
        public Item withDescription(String description) {
            Item copy = (Item) copy();
            copy.setDescription(description);
            return copy;
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

        @Override
        protected void copyProperties(IdentityAware<String> source, IdentityAware<String> target) {
            super.copyProperties(source, target);
            ((Item) target).thresholds = thresholds;
            ((Item) target).policies = EnumSet.copyOf(policies);
        }
    }

    /**
     * An enum which provides a policy on how the individual items are reported or handled.
     */
    public enum Policy {
        REPORT_IF_DOWNGRADE,
        REPORT_IF_WARNING,
        REPORT_IF_ERROR,
        RESTART_IF_NEEDED
    }

    private static final Set<Policy> EMPTY_POLICIES = EnumSet.noneOf(Policy.class);
}
