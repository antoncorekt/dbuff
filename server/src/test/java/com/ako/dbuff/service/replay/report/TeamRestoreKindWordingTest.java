package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRestoreKindWordingTest {
  private final TeamRestoreKindWording wording = new TeamRestoreKindWording();

  private static ItemEfficiency row(int count, double saves) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("hp", 2100.0);
    figures.put("mana", 1400.0);
    figures.put("allies", 3.0);
    figures.put("saves", saves);
    return new ItemEfficiency(
        0,
        "mekansm",
        "mekansm",
        "team-restore",
        0.0,
        count,
        900,
        1775L,
        0.5,
        null,
        figures,
        List.of("MANA_ESTIMATED"),
        List.of());
  }

  @Test
  void aRowReadsUsesHealthManaAlliesAndSaves() {
    assertThat(TeamRestoreKindWording.summary(row(9, 1)))
        .isEqualTo("9 uses, 2.1k HP and 1.4k mana to 3 allies, 1 save");
    assertThat(wording.shortVerdict(row(9, 0), "Mekansm"))
        .startsWith("Mekansm: 9 uses, 2.1k HP and 1.4k mana to 3 allies, no saves");
    assertThat(wording.longVerdict(row(9, 1))).contains("estimated from the item");
    assertThat(wording.complete(row(9, 1))).isTrue();
  }

  @Test
  void anItemNeverUsedSaysSo() {
    assertThat(wording.shortVerdict(row(0, 0), "Mekansm")).isEqualTo("Mekansm was never used");
  }
}
