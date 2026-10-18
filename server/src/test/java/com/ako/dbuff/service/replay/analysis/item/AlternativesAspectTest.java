package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Alternative;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlternativesAspectTest {
  private static final ItemEfficiencyEntry ENTRY =
      new ItemEfficiencyEntry(
          "radiance",
          "farming",
          List.of("radiance"),
          List.of("farm-rate", "alternatives"),
          List.of(),
          List.of("maelstrom"),
          Map.of());
  private static final ItemKits KITS = ItemTestData.kits(Map.of("radiance", 5_150L));

  private final AlternativeFinder finder = mock(AlternativeFinder.class);
  private final ItemEfficiencyFile file = new ItemEfficiencyFile("replay/items.yaml");
  private final AlternativesAspect aspect = new AlternativesAspect(finder, file);

  private static HeroInputs hero(long matchId) {
    HeroInputs plain =
        ItemTestData.hero(
            List.of(ItemTestData.change(1_500, StateChangeKind.ITEM_ADDED, "item_radiance")),
            List.of(),
            null,
            List.of(),
            List.of());
    return new HeroInputs(
        plain.slot(),
        plain.hero(),
        plain.changes(),
        plain.events(),
        null,
        List.of(),
        List.of(),
        plain.match(),
        matchId);
  }

  @Test
  void hasNoGainAndKeepsTheTwoMostBoughtCandidatesAsRows() {
    when(finder.find(any(), any()))
        .thenReturn(
            new Result(
                12,
                List.of(
                    new Alternative("maelstrom", 14, 0.06, 800.0),
                    new Alternative("manta", 3, null, null),
                    new Alternative("bfury", 2, null, null))));

    AspectPart part = aspect.measure(AspectContext.of(ENTRY, KITS, 3_000, hero(7), Map.of()));

    assertThat(part.gain()).isZero();
    assertThat(part.measured()).isTrue();
    assertThat(part.rows()).extracting(d -> d.target()).containsExactly("maelstrom", "manta");
    assertThat(part.rows().getFirst().figures())
        .containsEntry("games", 14.0)
        .containsEntry("winRateDelta", 0.06)
        .containsEntry("leadDelta", 800.0);
    assertThat(part.rows().get(1).figures()).containsOnlyKeys("games");
    assertThat(part.figures())
        .containsEntry("altOwnGames", 12.0)
        .containsEntry("altCandidates", 3.0);
  }

  @Test
  void aHeroWithoutAMatchIdIsNotLookedUp() {
    AspectPart part = aspect.measure(AspectContext.of(ENTRY, KITS, 3_000, hero(0), Map.of()));

    assertThat(part.rows()).isEmpty();
    verifyNoInteractions(finder);
  }

  @Test
  void theCatalogGivesRadianceItsFixedCandidates() {
    assertThat(file.entries())
        .filteredOn(e -> e.key().equals("radiance"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.alternatives()).containsExactly("maelstrom", "bfury", "manta");
              assertThat(e.aspects()).contains("alternatives");
            });
    assertThat(file.alternativeSettings())
        .containsKeys("altCostBand", "altTimeBand", "altMinGames");
  }
}
