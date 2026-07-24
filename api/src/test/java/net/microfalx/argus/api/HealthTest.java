package net.microfalx.argus.api;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthTest {

    @Test
    void normalizeClampsToBounds() {
        assertThat(Health.normalize(0f)).isEqualTo(Health.MIN);
        assertThat(Health.normalize(3f)).isEqualTo(3f);
        assertThat(Health.normalize(10f)).isEqualTo(Health.MAX);
    }

    @Test
    void emptyScoreReturnsHealthyDefaults() {
        Health health = new Health();
        assertThat(health.getScore()).isEqualTo(Health.MAX);
        assertThat(health.getLowest()).isEmpty();
        assertThat(health.getGroups()).isEmpty();
    }

    @Test
    void addAggregatesLowestItemAcrossGroups() {
        Health health = new Health();
        health.update("Database", "Connections", 4.5f);
        health.update("Messaging", "Broker", 1.75f);
        health.update("Database", "Latency", 3f);

        Health.Group database = health.getGroup("Database");
        Health.Group messaging = health.getGroup("Messaging");

        assertThat(database.getScore()).isEqualTo(3f);
        assertThat(database.getLowest()).map(Health.Item::getName).contains("Latency");
        assertThat(messaging.getScore()).isEqualTo(1.75f);
        assertThat(health.getScore()).isEqualTo(1.75f);
        assertThat(health.getLowest()).map(Health.Item::getName).contains("Broker");
        assertThat(health.getGroups()).extracting(Health.Group::getName)
                .containsExactlyInAnyOrder("Database", "Messaging");
    }

    @Test
    void groupWithoutItemsReturnsHealthyDefaults() {
        Health.Group group = Health.Group.create("database");
        assertThat(group.getScore()).isEqualTo(Health.MAX);
        assertThat(group.getLowest()).isEmpty();
    }

    @Test
    void itemNormalizesValueAndHasDefaultPolicy() {
        Health.Item item = Health.Item.create("Queue Depth", 100f);
        assertThat(item.getScore()).isEqualTo(Health.MAX);
        assertThat(item.getPolicies()).isEmpty();
    }

    @Test
    void withPolicyReturnsCopyWithAdditionalPolicyAndKeepsOriginalUnchanged() {
        Health.Item item = Health.Item.create("Queue Depth", 3f);
        Health.Item updated = item.withPolicy(Health.Policy.REPORT_IF_ERROR);

        assertThat(updated).isNotSameAs(item);
        assertThat(updated.getName()).isEqualTo(item.getName());
        assertThat(updated.getScore()).isEqualTo(item.getScore());
        assertThat(updated.getPolicies()).containsExactlyInAnyOrder(Health.Policy.REPORT_IF_ERROR);
        assertThat(updated.has(Health.Policy.REPORT_IF_ERROR)).isTrue();
    }

    @Test
    void itemCreatedFromThresholdsUsesThresholdName() {
        Thresholds thresholds = Thresholds.create("CPU Usage", 50f, 80f);
        Health.Item item = Health.Item.create(thresholds, 50f);
        assertThat(item.getName()).isEqualTo("CPU Usage");
        assertThat(item.getScore()).isEqualTo(Health.WARNING);
    }

    @Test
    void itemCreatedFromThresholdsNullThrowsException() {
        assertThatThrownBy(() -> Health.Item.create((Thresholds) null, 50f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemCreatedFromThresholdsWithPercentageComputesScoreAndDescription() {
        Thresholds thresholds = Thresholds.create("Memory", 50f, 80f, Unit.PERCENT);
        Health.Item item = Health.Item.create(thresholds, 500f, 1000f, Unit.BYTE);

        assertThat(item.getName()).isEqualTo("Memory");
        assertThat(item.getScore()).isEqualTo(Health.WARNING);
        assertThat(item.getDescription()).isNotBlank();
    }

    @Test
    void itemCreatedFromThresholdsWithPercentageRequiresPercentUnit() {
        Thresholds thresholds = Thresholds.create("CPU", 50f, 80f);
        assertThatThrownBy(() -> Health.Item.create(thresholds, 50f, 100f, Unit.BYTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERCENT");
    }

    @Test
    void withNameReturnsCopyWithDifferentName() {
        Health.Item item = Health.Item.create("Original", 3f);
        Health.Item renamed = item.withName("Renamed");

        assertThat(renamed).isNotSameAs(item);
        assertThat(renamed.getName()).isEqualTo("Renamed");
        assertThat(renamed.getScore()).isEqualTo(item.getScore());
        assertThat(item.getName()).isEqualTo("Original");
    }

    @Test
    void withDescriptionReturnsCopyWithDescription() {
        Health.Item item = Health.Item.create("CPU", 3f);
        Health.Item withDesc = item.withDescription("High CPU usage detected");

        assertThat(withDesc).isNotSameAs(item);
        assertThat(withDesc.getDescription()).isEqualTo("High CPU usage detected");
        assertThat(withDesc.getName()).isEqualTo(item.getName());
        assertThat(item.getDescription()).isNull();
    }

    @Test
    void updateWithItemAddsItemToGroup() {
        Health health = new Health();
        Health.Item item = Health.Item.create("Broker", 1.5f);
        health.update("Messaging", item);

        assertThat(health.getGroup("Messaging").getItems()).contains(item);
        assertThat(health.getScore()).isEqualTo(1.5f);
    }

    @Test
    void groupUpdateWithItemAddsItemToGroup() {
        Health.Group group = Health.Group.create("Database");
        Health.Item item = Health.Item.create("Connections", 3.5f);
        group.update(item);

        assertThat(group.getItems()).contains(item);
        assertThat(group.getScore()).isEqualTo(3.5f);
    }

    @Test
    void exposedCollectionsAreImmutable() {
        Health health = new Health();
        Health.Group group = health.getGroup("Database");
        group.update("Connections", 3f);
        group.update(Health.Item.create("Threads", 1.75f));

        Collection<Health.Group> groups = health.getGroups();
        Collection<Health.Item> items = group.getItems();
        Set<Health.Policy> policies = Health.Item.create("Queue Depth", 3f).getPolicies();

        assertThatThrownBy(() -> groups.add(Health.Group.create("Messaging")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> items.add(Health.Item.create("Latency", 2f)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policies.add(Health.Policy.REPORT_IF_ERROR))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

