package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoulRingKindWordingTest {
  private final SoulRingKindWording wording = new SoulRingKindWording();

  private static ItemEfficiency row(int count, double deaths, List<Detail> rows) {
    return new ItemEfficiency(
        0,
        "soul_ring",
        "soul_ring",
        "self-harm",
        100.0,
        count,
        -402,
        805L,
        -0.5,
        null,
        Map.of("hp", -4080.0, "mana", 3600.0, "selfDeaths", deaths),
        List.of(),
        rows);
  }

  @Test
  void theLineGivesUsesManaHealthCostAndTheDeathItCaused() {
    ItemEfficiency row =
        row(24, 1, List.of(new Detail("SOUL_RING_DEATH", 1390, null, Map.of("useSecond", 1385.0))));

    assertThat(wording.shortVerdict(row, "Axe's Soul Ring"))
        .isEqualTo(
            "Axe's Soul Ring: 24 uses, 3.6k mana, cost 4.1k HP; 1 death it caused at 23:10, -0.5x"
                + " its cost");
  }

  @Test
  void noDeathIsSaidSo() {
    assertThat(wording.longVerdict(row(24, 0, List.of())))
        .isEqualTo("24 uses, 3.6k mana, cost 4.1k HP; no death it caused, worth -0.5x its cost");
  }

  @Test
  void anUnusedRingIsNeverUsed() {
    assertThat(wording.shortVerdict(row(0, 0, List.of()), "Axe's Soul Ring"))
        .isEqualTo("Axe's Soul Ring was never used");
  }
}
