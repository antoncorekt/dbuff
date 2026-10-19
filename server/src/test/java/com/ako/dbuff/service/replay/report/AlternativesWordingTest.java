package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlternativesWordingTest {
  private static ItemEfficiency row(Detail... details) {
    return new ItemEfficiency(
        0,
        "radiance",
        "radiance",
        "farm-rate",
        1_500.0,
        0,
        0,
        5_150L,
        1.0,
        null,
        Map.of(),
        List.of(),
        List.of(details));
  }

  private static Detail alternative(String key, Map<String, Double> figures) {
    return new Detail("ALTERNATIVE", 1_500, key, figures);
  }

  @Test
  void popularItemsAreNamedWithoutNumbers() {
    ItemEfficiency row =
        row(
            new Detail("POPULAR", 1_500, "maelstrom", Map.of()),
            new Detail("POPULAR", 1_500, "bfury", Map.of()));

    assertThat(AlternativesWording.suffix(row, key -> "maelstrom".equals(key) ? "Maelstrom" : null))
        .isEqualTo("; popular for this hero at this point: Maelstrom or bfury");
  }

  @Test
  void namesTheUsualAlternativesWithTheirGamesAndWinRateOrFewGames() {
    ItemEfficiency row =
        row(
            alternative("maelstrom", Map.of("games", 14.0, "winRateDelta", 0.06)),
            alternative("manta", Map.of("games", 3.0)));

    assertThat(AlternativesWording.suffix(row, key -> "maelstrom".equals(key) ? "Maelstrom" : null))
        .isEqualTo(
            "; same gold usually goes to Maelstrom (14 games, +6% win rate) or manta (few games)");
  }

  @Test
  void aNegativeDeltaKeepsItsSign() {
    assertThat(
            AlternativesWording.suffix(
                row(alternative("bfury", Map.of("games", 9.0, "winRateDelta", -0.04))),
                key -> "Battle Fury"))
        .isEqualTo("; same gold usually goes to Battle Fury (9 games, -4% win rate)");
  }

  @Test
  void aRowWithoutAlternativesAddsNothing() {
    assertThat(AlternativesWording.suffix(row(), key -> key)).isEmpty();
  }
}
