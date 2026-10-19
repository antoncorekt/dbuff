package com.ako.dbuff.service.replay.analysis.item;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemEfficiencyFileTest {

  private static ItemEfficiencyFile file(String yaml) {
    return new ItemEfficiencyFile(
        () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
  }

  @Test
  void theShippedFileHoldsTheStickTheMidasAndTheFarmingGroup() {
    var entries = new ItemEfficiencyFile("replay/items.yaml").entries();

    assertThat(entries)
        .extracting(ItemEfficiencyEntry::key, ItemEfficiencyEntry::aspects)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("magic_stick", List.of("charge-restore", "save")),
            org.assertj.core.groups.Tuple.tuple("hand_of_midas", List.of("midas-uses")),
            org.assertj.core.groups.Tuple.tuple(
                "farming_items",
                List.of("farm-rate", "item-damage", "stat-uplift", "alternatives")),
            org.assertj.core.groups.Tuple.tuple(
                "radiance", List.of("farm-rate", "item-damage", "alternatives", "saving-cost")),
            org.assertj.core.groups.Tuple.tuple("ward_observer", List.of("vision")),
            org.assertj.core.groups.Tuple.tuple("ward_sentry", List.of("vision")),
            org.assertj.core.groups.Tuple.tuple("dust", List.of("vision")),
            org.assertj.core.groups.Tuple.tuple("gem", List.of("vision")));
    assertThat(entries)
        .extracting(ItemEfficiencyEntry::category)
        .containsExactly(
            "sustain", "farming", "farming", "farming", "vision", "vision", "vision", "vision");
    assertThat(entries.get(0).items()).containsExactly("magic_stick", "magic_wand");
    assertThat(entries.get(2).items()).containsExactly("bfury", "maelstrom", "mjollnir");
  }

  @Test
  void theShippedAspectsAndParametersAreAllKnown() {
    var entries = new ItemEfficiencyFile("replay/items.yaml").entries();
    var aspects =
        java.util.List.<ItemAspect>of(
            new ChargeRestoreAspect(),
            new SaveAspect(),
            new MidasUsesAspect(null),
            new FarmRateAspect(),
            new ItemDamageAspect(),
            new StatUpliftAspect(),
            new AlternativesAspect(null, null),
            new SavingCostAspect(),
            new VisionAspect(new com.ako.dbuff.service.replay.analysis.vision.VisionProperties()));

    for (ItemEfficiencyEntry entry : entries) {
      var own =
          entry.aspects().stream()
              .map(name -> aspects.stream().filter(a -> a.name().equals(name)).findFirst())
              .map(java.util.Optional::orElseThrow)
              .toList();
      assertThat(own.stream().flatMap(a -> a.parameterNames().stream()))
          .containsAll(entry.parameters().keySet());
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

  private static final String OK = "  - {key: a, category: farming, aspects: [k], items: [x]}";

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
    assertThat(entry.aspects()).containsExactly("k");
  }

  @Test
  void theOptionalFieldsAreRead() {
    var entry =
        file(entries(
                "  - key: a",
                "    category: farming",
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
        .hasMessageContaining("'a' lists no aspects");
    assertThatThrownBy(
            () -> file(entries("  - {key: a, category: farming, aspects: [k]}")).entries())
        .hasMessageContaining("'a' lists no items");
    assertThatThrownBy(
            () ->
                file(entries("  - {key: a, category: farming, aspects: [k], items: [item_x]}"))
                    .entries())
        .hasMessageContaining("no item_");
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, aspects: [k], items: [x],"
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
            () -> file(entries("  - {key: a, category: nope, aspects: [k], items: [x]}")).entries())
        .hasMessageContaining("'a' has unknown category 'nope'");
    assertThatThrownBy(() -> file(entries("  - {key: a, aspects: [k], items: [x]}")).entries())
        .hasMessageContaining("'a' has unknown category 'null'");
  }

  @Test
  void aMalformedWinStatsNamesTheEntry() {
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, aspects: [k], items: [x],"
                            + " winStats: [[x], []]}"))
                    .entries())
        .hasMessageContaining("'a' has a malformed winStats group");
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: farming, aspects: [k], items: [x],"
                            + " winStats: [[item_x]]}"))
                    .entries())
        .hasMessageContaining("'a' has winStats item 'item_x'");
  }

  @Test
  void allProblemsAreReportedTogether() {
    assertThatThrownBy(
            () ->
                file(entries(
                        "  - {key: a, category: nope, aspects: [k], items: [x]}",
                        "  - {key: b, category: farming, aspects: [k], items: [item_y]}"))
                    .entries())
        .hasMessageContaining("'a' has unknown category")
        .hasMessageContaining("'b' has item 'item_y'");
  }

  @Test
  void theShippedCategoriesAreListed() {
    assertThat(new ItemEfficiencyFile("replay/items.yaml").categories())
        .containsKeys("farming", "sustain", "vision");
  }

  @Test
  void anUnknownPriceNameFailsOnceAndIsNamed() {
    ItemEfficiencyFile bad =
        file(
            """
            prices: {pokePrise: 1, xpWeight: 2}
            categories: {c: C}
            entries:
              - {key: a, category: c, items: [x], aspects: [save]}
            """);

    assertThatThrownBy(bad::entries)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown price 'pokePrise'")
        .hasMessageNotContaining("xpWeight");
  }

  @Test
  void categoriesAndEntriesKeepTheOrderOfTheFile() {
    ItemEfficiencyFile ordered =
        file(
            """
            categories: {zeta: Z, alpha: A, mid: M, beta: B}
            entries:
              - {key: q, category: mid, items: [x], aspects: [save]}
              - {key: b, category: zeta, items: [y], aspects: [save]}
              - {key: m, category: alpha, items: [z], aspects: [save]}
            """);

    assertThat(ordered.categories().keySet()).containsExactly("zeta", "alpha", "mid", "beta");
    assertThat(ordered.entries())
        .extracting(ItemEfficiencyEntry::key)
        .containsExactly("q", "b", "m");
    assertThat(ordered.selectableNames())
        .containsExactly("zeta", "alpha", "mid", "beta", "q", "b", "m");
  }
}
