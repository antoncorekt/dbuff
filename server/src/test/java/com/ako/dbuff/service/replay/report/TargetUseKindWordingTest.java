package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetUseKindWordingTest {
  private final TargetUseKindWording wording = new TargetUseKindWording();

  private static Detail saved(String target) {
    return new Detail("CAST", 100, target, Map.of("saved", 1.0));
  }

  private static ItemEfficiency row(int count, Map<String, Double> figures, List<Detail> rows) {
    return new ItemEfficiency(
        0,
        "glimmer_cape",
        "glimmer_cape",
        "target-use",
        0.0,
        count,
        0,
        1950L,
        0.5,
        null,
        figures,
        List.of(),
        rows);
  }

  @Test
  void namesWhomTheCastsSavedAndTheKillsTheySetUp() {
    ItemEfficiency row =
        row(
            6,
            Map.of("saves", 3.0, "setUps", 1.0),
            List.of(
                saved("npc_dota_hero_lion"),
                saved("npc_dota_hero_lion"),
                saved(null),
                new Detail("CAST", 200, "npc_dota_hero_axe", Map.of("setUp", 1.0))));

    assertThat(wording.longVerdict(row))
        .startsWith("6 casts, saved Lion twice and himself once, 1 kill set up, worth");
    assertThat(wording.shortVerdict(row, "Glimmer Cape"))
        .startsWith("Glimmer Cape: 6 casts, saved Lion twice and himself once, 1 kill set up");
    assertThat(wording.complete(row)).isTrue();
  }

  @Test
  void noSavesAndNoUses() {
    assertThat(wording.longVerdict(row(2, Map.of("saves", 0.0), List.of())))
        .startsWith("2 casts, no saves, worth");
    assertThat(wording.longVerdict(row(0, Map.of(), List.of()))).startsWith("bought, never used");
  }

  @Test
  void heroNamesAreWords() {
    assertThat(TargetUseKindWording.hero("npc_dota_hero_crystal_maiden"))
        .isEqualTo("Crystal Maiden");
    assertThat(TargetUseKindWording.hero(null)).isEqualTo("himself");
    assertThat(TargetUseKindWording.hero("npc_dota_hero_queenofpain")).isEqualTo("Queen of Pain");
  }
}
