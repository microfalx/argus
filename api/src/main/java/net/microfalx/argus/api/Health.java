package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.IdentityAware;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.NumberUtils;

import java.util.*;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.CollectionUtils.immutableCollection;
import static net.microfalx.lang.CollectionUtils.immutableSet;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
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
@ToString
public final class Health extends IdentityAware<Long> {

    public static final float MIN = 1;
    public static final float MAX = 5;
    public static final float WARNING = 2;
    public static final float ERROR = 4;
    public static final float BAD = 1.5f;
    public static final float WITH_ISSUES = 2.5f;

    private final Map<String, Group> groups = new LinkedHashMap<>();

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
     * Returns a group by its name.
     *
     * @param name the name of the group
     * @return a non-null instance
     */
    public Group getGroup(String name) {
        requireNotEmpty(name);
        return groups.computeIfAbsent(toIdentifier(name), s -> {
            Group group = new Group(name);
            group.order = groups.size() * 10;
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
     * Returns the item with the lowest score across all groups.
     *
     * @return an optional item
     */
    public Optional<Item> getLowest() {
        return groups.values().stream().flatMap(group -> group.items.stream())
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
        getGroup(group).update(item, score);
    }

    /**
     * Registers an item with its score.
     *
     * @param group the name of the group which owns the item
     * @param item  the item
     */
    public void update(String group, Item item) {
        requireNotEmpty(group);
        requireNotEmpty(item);
        getGroup(group).update(item);
    }

    @Getter
    @ToString
    public static class Group implements Nameable {

        private final String name;
        private final Collection<Item> items = new ArrayList<>();

        /**
         * Holds the order of the group within the score.
         */
        private int order;

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
            this.name = name;
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
         * Returns the score for this group, which is the score of the item with
         * the lowest score.
         *
         * @return a value between 1 and 5
         */
        public float getScore() {
            return getLowest().map(Item::getScore).orElse(5f);
        }

        /**
         * Returns the item with the lowest score in this group.
         *
         * @return an optional item
         */
        public Optional<Item> getLowest() {
            return items.stream().min(Comparator.comparing(Item::getScore));
        }

        /**
         * Registers an item with its score and a {@link Policy} of {@link Policy#REPORT_IF_DOWNGRADE}..
         *
         * @param name  the name of the item
         * @param score the score of the item
         */
        public void update(String name, float score) {
            Item item = new Item(name, score);
            items.add(item);
        }

        /**
         * Registers an item with its score.
         *
         * @param item the item
         */
        public void update(Item item) {
            requireNonNull(item);
            items.add(item);
        }
    }

    @Getter
    @ToString
    public static class Item implements Nameable, Descriptable, Cloneable {

        private String name;
        private String description;

        /**
         * The score of the item
         */
        private final float score;

        /**
         * The policy associated with the item, which determines how it is reported in the score.
         */
        private Set<Policy> policies = EMPTY_POLICIES;

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
            return new Item(thresholds.getName(), score);
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
            String description = String.format("%s (%s of %s)", Unit.PERCENT.format(percent),
                    unit.format(value), unit.format(maximum));
            return new Item(thresholds.getName(), score).withDescription(description);
        }

        private Item(String name, float score) {
            requireNotEmpty(name);
            this.name = name;
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
            Item copy = copy();
            copy.name = name;
            return copy;
        }

        /**
         * Creates a new item with a different description.
         *
         * @param description the description
         * @return a new instance
         */
        public Item withDescription(String description) {
            Item copy = copy();
            copy.description = description;
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
            Item copy = copy();
            copy.policies.add(policy);
            return copy;
        }

        private Item copy() {
            try {
                Item clone = (Item) clone();
                clone.policies = EnumSet.copyOf(policies);
                return clone;
            } catch (CloneNotSupportedException e) {
                return rethrowExceptionAndReturn(e);
            }
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
