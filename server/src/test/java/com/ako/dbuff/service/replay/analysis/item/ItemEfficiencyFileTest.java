package com.ako.dbuff.service.replay.analysis.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ItemEfficiencyFileTest {

  private static ItemEfficiencyFile file(String yaml) {
    return new ItemEfficiencyFile(
        () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
  }

  @Test
  void theShippedFileHoldsTheStickTheMidasAndTheFarmingGroup() {
    var entries = new ItemEfficiencyFile("replay/items.yaml").entries();

    assertThat(entries)
        .extracting(ItemEfficiencyEntry::key, ItemEfficiencyEntry::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("magic_stick", "magic-stick"),
            org.assertj.core.groups.Tuple.tuple("hand_of_midas", "hand-of-midas"),
            org.assertj.core.groups.Tuple.tuple("farming_items", "farming-group"));
    assertThat(entries)
        .extracting(ItemEfficiencyEntry::category)
        .containsExactly("sustain", "farming", "farming");
    assertThat(entries.get(0).items()).containsExactly("magic_stick", "magic_wand");
    assertThat(entries.get(2).items()).containsExactly("bfury", "maelstrom", "mjollnir");
  }

  @Test
  void theShippedParametersAreAllKnownToTheirEvaluators() {
    var entries = new ItemEfficiencyFile("replay/items.yaml").entries();
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
            "missing.yaml");

    assertThat(opened).hasValue(0);
    assertThatThrownBy(lazy::entries).hasMessageContaining("not there");
  }

  private static final String CATEGORIES = "categories: {farming: Farming}\n";

  private static final String OK = "  - {key: a, category: farming, kind: k, items: [x]}";

  private static String entries(String... lines) {
    return CATEGORIES + "entries:\n" + String.join("\n", lines) + "\n";
  }

  @Test
  void anEntryWithoutParametersGetsEmptyDefaults() {
    var entry = file(entries(OK)).entries().getFirst();

    assertThat(entry.parameters()).isEmpty();
    assertThat(entry.parameter("p", 7.0)).isEqualTo(7.0);
    assertThat(entry.category()).isEqualTo("farming");
    assertThat(entry.winStats()).isEmpty();
    assertThat(entry.alternatives()).isEmpty();
    assertThat(entry.aspects()).isEmpty();
  }

  @Test
  void theOptionalFieldsAreRead() {
    var entry =
        file(entries(
                "  - key: a",
                "    category: farming",
                "    kind: k",
                "    items: [x]",
                "    aspects: [farm-rate]",
                "    winStats: [[x], [x, y]]",
                "    alternatives: [z]"))
            .entries()
            .getFirst();

    assertThat(entry.winStats()).containsExactly(List.of("x"), List.of("x", "y"));
    assertThat(entry.alternatives()).containsExactly("z");
    assertThat(entry.aspects()).containsExactly("farm-rate");
  }

  @Test
  void aBadFileNamesTheProblem() {
    assertThatThrownBy(() -> file("categories: {farming: F}\nentries: []\n").entries())
        .hasMessageContaining("test.yaml")
        .hasMessageContaining("no entries");
    assertThatThrownBy(() -> file("entries:\n" + OK + "\n").entries())
        .hasMessageContaining("no categories");
    assertThatThrownBy(() -> file(entries(OK, OK)).entries())
        .hasMessageContaining("'a' is listed twice");
    assertThatThrownBy(() -> file(entries("  - {key: a, category: farming, items: [x]}")).entries())
        .hasMessageContaining("'a' has no kind");
    assertThatThrownBy(() -> file(entries("  - {key: a, category: farming, kind: k}")).entries())
        .hasMessageContaining("'a' lists no items");
    assertThatThrownBy(
            () ->
                file(entries("  - {key: a, category: farming, kind: k, items: [item_x]}"))
                    .entries())
        .hasMessageContaining("no item_");
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, kind: k, items: [x],"
                            + " parameters: {p: -1}}"))
                    .entries())
        .hasMessageContaining("parameter 'p'");
    assertThatThrownBy(() -> file(entries(OK) + "x: 1\n").entries())
        .hasMessageContaining("test.yaml");
    assertThatThrownBy(() -> file("a: [").entries()).hasMessageContaining("cannot be read");
  }

  @Test
  void anUnknownCategoryIsNamedWithItsEntry() {
    assertThatThrownBy(
            () -> file(entries("  - {key: a, category: nope, kind: k, items: [x]}")).entries())
        .hasMessageContaining("'a' has unknown category 'nope'");
    assertThatThrownBy(() -> file(entries("  - {key: a, kind: k, items: [x]}")).entries())
        .hasMessageContaining("'a' has unknown category 'null'");
  }

  @Test
  void aMalformedWinStatsNamesTheEntry() {
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, kind: k, items: [x],"
                            + " winStats: [[x], []]}"))
                    .entries())
        .hasMessageContaining("'a' has a malformed winStats group");
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, kind: k, items: [x],"
                            + " winStats: [[item_x]]}"))
                    .entries())
        .hasMessageContaining("'a' has winStats item 'item_x'");
  }

  @Test
  void allProblemsAreReportedTogether() {
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: nope, kind: k, items: [x]}",
                        "  - {key: b, category: farming, kind: k, items: [item_y]}"))
                    .entries())
        .hasMessageContaining("'a' has unknown category")
        .hasMessageContaining("'b' has item 'item_y'");
  }

  @Test
  void theShippedCategoriesAreListed() {
    assertThat(new ItemEfficiencyFile("replay/items.yaml").categories())
        .containsKeys("farming", "sustain");
  }
}
