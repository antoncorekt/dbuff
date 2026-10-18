package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.death;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hit;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class SoulRingAspectsTest {
  private static final String RING = "item_soul_ring";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "soul_ring",
          List.of(SelfHarmAspect.NAME, ChargeRestoreAspect.NAME),
          List.of("soul_ring"),
          Map.of("healthCost", 170.0));
  private static final ItemKits KITS =
      key ->
          "soul_ring".equals(key)
              ? Optional.of(
                  ItemConstant.builder()
                      .cost(805L)
                      .attrib(List.of(Map.of("key", "mana_gain", "value", "170")))
                      .build())
              : Optional.empty();

  private final List<ItemAspect> aspects = List.of(new SelfHarmAspect(), new ChargeRestoreAspect());

  private ItemEfficiency evaluate(List<CombatEvent> events) {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    for (int second : new int[] {100, 150}) {
      track.getManaPercent()[sampleOf(second)] = 50;
      track.getMana()[sampleOf(second)] = 200;
    }
    HeroInputs hero =
        hero(
            List.of(change(-60, StateChangeKind.ITEM_ADDED, RING)),
            events,
            track,
            List.of(),
            List.of());
    List<ItemEfficiency> rows = ItemTestData.judge(ENTRY, aspects, KITS, 900, List.of(hero));
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  @Test
  void usesAreManaGainedAndHealthSpent() {
    ItemEfficiency result = evaluate(List.of(use(100, RING, ""), use(150, RING, "")));

    assertThat(result.kind()).isEqualTo("self-harm");
    assertThat(result.count()).isEqualTo(2);
    assertThat(result.figures())
        .containsEntry("hp", -340.0)
        .containsEntry("mana", 340.0)
        .containsEntry("selfDeaths", 0.0);
    assertThat(result.gain()).isEqualTo(0, org.assertj.core.data.Offset.offset(0.001));
    assertThat(result.flags()).doesNotContain("SOUL_RING_DEATH", "HP_UNCAPPED");
  }

  @Test
  void aDeathAfterAUseWithLittleEnemyDamageIsCausedByTheRing() {
    ItemEfficiency result =
        evaluate(List.of(hit(199, 20, 150), use(200, RING, ""), hit(203, 30, 90), death(205)));

    assertThat(result.figures()).containsEntry("selfDeaths", 1.0);
    assertThat(result.flags()).contains("SOUL_RING_DEATH");
    assertThat(result.gain()).isLessThan(0);
    assertThat(result.rows())
        .anyMatch(r -> r.label().equals("SOUL_RING_DEATH") && r.seconds() == 205);
  }

  @Test
  void aDeathUnderHeavyEnemyDamageIsNotCausedByTheRing() {
    ItemEfficiency result =
        evaluate(List.of(hit(199, 20, 150), use(200, RING, ""), hit(203, 400, 0), death(204)));

    assertThat(result.figures()).containsEntry("selfDeaths", 0.0);
    assertThat(result.flags()).doesNotContain("SOUL_RING_DEATH");
  }

  @Test
  void aDeathLongAfterTheUseIsNotCausedByTheRing() {
    ItemEfficiency result = evaluate(List.of(hit(199, 20, 150), use(200, RING, ""), death(230)));

    assertThat(result.figures()).containsEntry("selfDeaths", 0.0);
  }
}
