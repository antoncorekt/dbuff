package com.ako.dbuff.service.replay.analysis.item;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemEfficiencyFileTest {

  private static ItemEfficiencyFile file(String json) {
    return new ItemEfficiencyFile(
        () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }

  @Test
  void theShippedFileHoldsTheStickTheMidasAndTheFarmingGroup() {
    var entries = new ItemEfficiencyFile("replay/item-efficiency.json").entries();

    assertThat(entries)
        .extracting(ItemEfficiencyEntry::key, ItemEfficiencyEntry::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("magic_stick", "magic-stick"),
            org.assertj.core.groups.Tuple.tuple("hand_of_midas", "hand-of-midas"),
            org.assertj.core.groups.Tuple.tuple("farming_items", "farming-group"));
    assertThat(entries.get(0).items()).containsExactly("magic_stick", "magic_wand");
    assertThat(entries.get(2).items()).containsExactly("bfury", "maelstrom", "mjollnir");
  }

  @Test
  void theShippedParametersAreAllKnownToTheirEvaluators() {
    var entries = new ItemEfficiencyFile("replay/item-efficiency.json").entries();
    var evaluators =
        java.util.List.of(
            new MagicStickEvaluator(), new HandOfMidasEvaluator(null), new FarmingGroupEvaluator());

    for (ItemEfficiencyEntry entry : entries) {
      var evaluator = evaluators.stream().filter(e -> e.supports(entry)).findFirst().orElseThrow();
      assertThat(evaluator.parameterNames()).containsAll(entry.parameters().keySet());
    }
  }

  @Test
  void nothingIsReadUntilTheFirstUse() {
    AtomicInteger opened = new AtomicInteger();
    ItemEfficiencyFile lazy =
        new ItemEfficiencyFile(
            () -> {
              opened.incrementAndGet();
              throw new IllegalStateException("not there");
            },
            "missing.json");

    assertThat(opened).hasValue(0);
    assertThatThrownBy(lazy::entries).hasMessageContaining("not there");
  }

  @Test
  void anEntryWithoutParametersGetsAnEmptyMap() {
    var entries =
        file("{\"status\": \"s\", \"entries\": [{\"key\": \"a\", \"kind\": \"k\", \"items\":"
                + " [\"x\"]}]}")
            .entries();

    assertThat(entries.getFirst().parameters()).isEmpty();
    assertThat(entries.getFirst().parameter("p", 7.0)).isEqualTo(7.0);
  }

  @Test
  void aBadFileNamesTheProblem() {
    String ok = "{\"key\": \"a\", \"kind\": \"k\", \"items\": [\"x\"]}";
    assertThatThrownBy(() -> file("{\"entries\": [" + ok + "]}").entries())
        .hasMessageContaining("test.json")
        .hasMessageContaining("status");
    assertThatThrownBy(() -> file("{\"status\": \"s\", \"entries\": []}").entries())
        .hasMessageContaining("no entries");
    assertThatThrownBy(
            () -> file("{\"status\": \"s\", \"entries\": [" + ok + "," + ok + "]}").entries())
        .hasMessageContaining("'a' is listed twice");
    assertThatThrownBy(
            () ->
                file("{\"status\": \"s\", \"entries\": [{\"key\": \"a\", \"items\": [\"x\"]}]}")
                    .entries())
        .hasMessageContaining("'a' has no kind");
    assertThatThrownBy(
            () ->
                file("{\"status\": \"s\", \"entries\": [{\"key\": \"a\", \"kind\": \"k\"}]}")
                    .entries())
        .hasMessageContaining("'a' lists no items");
    assertThatThrownBy(
            () ->
                file("{\"status\": \"s\", \"entries\": [{\"key\": \"a\", \"kind\": \"k\","
                        + " \"items\": [\"item_x\"]}]}")
                    .entries())
        .hasMessageContaining("no item_");
    assertThatThrownBy(
            () ->
                file("{\"status\": \"s\", \"entries\": [{\"key\": \"a\", \"kind\": \"k\","
                        + " \"items\": [\"x\"], \"parameters\": {\"p\": -1}}]}")
                    .entries())
        .hasMessageContaining("parameter 'p'");
    assertThatThrownBy(
            () -> file("{\"status\": \"s\", \"entries\": [" + ok + "], \"x\": 1}").entries())
        .hasMessageContaining("test.json");
    assertThatThrownBy(() -> file("not json").entries()).hasMessageContaining("cannot be read");
  }
}
