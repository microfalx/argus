package net.microfalx.argus.api;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreTest {

    @Test
    void normalizeClampsToBounds() {
        assertThat(Score.normalize(0f)).isEqualTo(Score.MIN);
        assertThat(Score.normalize(3f)).isEqualTo(3f);
        assertThat(Score.normalize(10f)).isEqualTo(Score.MAX);
    }

    @Test
    void emptyScoreReturnsHealthyDefaults() {
        Score score = new Score();
        assertThat(score.getValue()).isEqualTo(Score.MAX);
        assertThat(score.getLowest()).isEmpty();
        assertThat(score.getGroups()).isEmpty();
    }

    @Test
    void addAggregatesLowestItemAcrossGroups() {
        Score score = new Score();
        score.update("Database", "Connections", 4.5f);
        score.update("Messaging", "Broker", 1.75f);
        score.update("Database", "Latency", 3f);

        Score.Group database = score.getGroup("Database");
        Score.Group messaging = score.getGroup("Messaging");

        assertThat(database.getValue()).isEqualTo(3f);
        assertThat(database.getLowest()).map(Score.Item::getName).contains("Latency");
        assertThat(messaging.getValue()).isEqualTo(1.75f);
        assertThat(score.getValue()).isEqualTo(1.75f);
        assertThat(score.getLowest()).map(Score.Item::getName).contains("Broker");
        assertThat(score.getGroups()).extracting(Score.Group::getName)
                .containsExactlyInAnyOrder("Database", "Messaging");
    }

    @Test
    void groupWithoutItemsReturnsHealthyDefaults() {
        Score.Group group = Score.Group.create("database");
        assertThat(group.getValue()).isEqualTo(Score.MAX);
        assertThat(group.getLowest()).isEmpty();
    }

    @Test
    void itemNormalizesValueAndHasDefaultPolicy() {
        Score.Item item = Score.Item.create("Queue Depth", 100f);
        assertThat(item.getValue()).isEqualTo(Score.MAX);
        assertThat(item.getPolicies()).isEmpty();
    }

    @Test
    void withPolicyReturnsCopyWithAdditionalPolicyAndKeepsOriginalUnchanged() {
        Score.Item item = Score.Item.create("Queue Depth", 3f);
        Score.Item updated = item.withPolicy(Score.Policy.REPORT_IF_ERROR);

        assertThat(updated).isNotSameAs(item);
        assertThat(updated.getName()).isEqualTo(item.getName());
        assertThat(updated.getValue()).isEqualTo(item.getValue());
        assertThat(updated.getPolicies()).containsExactlyInAnyOrder(Score.Policy.REPORT_IF_ERROR);
        assertThat(updated.has(Score.Policy.REPORT_IF_ERROR)).isTrue();
    }

    @Test
    void itemCreatedFromThresholdsUsesThresholdName() {
        Thresholds thresholds = Thresholds.create("CPU Usage", 50f, 80f);
        Score.Item item = Score.Item.create(thresholds, 50f);
        assertThat(item.getName()).isEqualTo("CPU Usage");
        assertThat(item.getValue()).isEqualTo(Score.WARNING);
    }

    @Test
    void itemCreatedFromThresholdsNullThrowsException() {
        assertThatThrownBy(() -> Score.Item.create((Thresholds) null, 50f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemCreatedFromThresholdsWithPercentageComputesScoreAndDescription() {
        Thresholds thresholds = Thresholds.create("Memory", 50f, 80f, Unit.PERCENT);
        Score.Item item = Score.Item.create(thresholds, 500f, 1000f, Unit.BYTE);

        assertThat(item.getName()).isEqualTo("Memory");
        assertThat(item.getValue()).isEqualTo(Score.WARNING);
        assertThat(item.getDescription()).isNotBlank();
    }

    @Test
    void itemCreatedFromThresholdsWithPercentageRequiresPercentUnit() {
        Thresholds thresholds = Thresholds.create("CPU", 50f, 80f);
        assertThatThrownBy(() -> Score.Item.create(thresholds, 50f, 100f, Unit.BYTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERCENT");
    }

    @Test
    void withNameReturnsCopyWithDifferentName() {
        Score.Item item = Score.Item.create("Original", 3f);
        Score.Item renamed = item.withName("Renamed");

        assertThat(renamed).isNotSameAs(item);
        assertThat(renamed.getName()).isEqualTo("Renamed");
        assertThat(renamed.getValue()).isEqualTo(item.getValue());
        assertThat(item.getName()).isEqualTo("Original");
    }

    @Test
    void withDescriptionReturnsCopyWithDescription() {
        Score.Item item = Score.Item.create("CPU", 3f);
        Score.Item withDesc = item.withDescription("High CPU usage detected");

        assertThat(withDesc).isNotSameAs(item);
        assertThat(withDesc.getDescription()).isEqualTo("High CPU usage detected");
        assertThat(withDesc.getName()).isEqualTo(item.getName());
        assertThat(item.getDescription()).isNull();
    }

    @Test
    void updateWithItemAddsItemToGroup() {
        Score score = new Score();
        Score.Item item = Score.Item.create("Broker", 1.5f);
        score.update("Messaging", item);

        assertThat(score.getGroup("Messaging").getItems()).contains(item);
        assertThat(score.getValue()).isEqualTo(1.5f);
    }

    @Test
    void groupUpdateWithItemAddsItemToGroup() {
        Score.Group group = Score.Group.create("Database");
        Score.Item item = Score.Item.create("Connections", 3.5f);
        group.update(item);

        assertThat(group.getItems()).contains(item);
        assertThat(group.getValue()).isEqualTo(3.5f);
    }

    @Test
    void exposedCollectionsAreImmutable() {
        Score score = new Score();
        Score.Group group = score.getGroup("Database");
        group.update("Connections", 3f);
        group.update(Score.Item.create("Threads", 1.75f));

        Collection<Score.Group> groups = score.getGroups();
        Collection<Score.Item> items = group.getItems();
        Set<Score.Policy> policies = Score.Item.create("Queue Depth", 3f).getPolicies();

        assertThatThrownBy(() -> groups.add(Score.Group.create("Messaging")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> items.add(Score.Item.create("Latency", 2f)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policies.add(Score.Policy.REPORT_IF_ERROR))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

