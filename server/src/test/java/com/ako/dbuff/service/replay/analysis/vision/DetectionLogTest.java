package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionLogTest {
  private static CombatEvent dust(double at, String hero) {
    return new CombatEvent(CombatEventKind.ITEM, at, hero, "", "item_dust", 1, 0, 0, 0, 0, 0, 0, 0);
  }

  private static ReplayStateChangeDomain change(
      long slot, double at, StateChangeKind kind, String item) {
    return ReplayStateChangeDomain.builder()
        .playerSlot(slot)
        .gameSeconds(at)
        .kind(kind.name())
        .name(item)
        .build();
  }

  @Test
  void dustUsedInTheRangeByTheSlotsAskedAbout() {
    DetectionLog log =
        new DetectionLog(
            List.of(dust(100, "npc_dota_hero_axe"), dust(300, "npc_dota_hero_axe")),
            Map.of("npc_dota_hero_axe", 128L),
            List.of());

    assertThat(log.dustUsed(Set.of(128L), 90, 110)).isTrue();
    assertThat(log.dustUsed(Set.of(128L), 110, 290)).isFalse();
    assertThat(log.dustUsed(Set.of(1L), 90, 110)).isFalse();
  }

  @Test
  void whatAHeroHeldAtATimeFollowsHisInventoryChanges() {
    DetectionLog log =
        new DetectionLog(
            List.of(),
            Map.of(),
            List.of(
                change(1, 50, StateChangeKind.ITEM_ADDED, "item_dust"),
                change(1, 80, StateChangeKind.ITEM_ADDED, "item_ward_sentry"),
                change(1, 120, StateChangeKind.ITEM_REMOVED, "item_dust"),
                change(2, 10, StateChangeKind.ITEM_ADDED, "item_gem"),
                change(1, 10, StateChangeKind.ITEM_ADDED, "item_blink")));

    assertThat(log.held(Set.of(1L), 100)).containsExactly("dust", "sentry");
    assertThat(log.held(Set.of(1L), 130)).containsExactly("sentry");
    assertThat(log.held(Set.of(1L), 40)).isEmpty();
    assertThat(log.held(Set.of(1L, 2L), 130)).containsExactly("gem", "sentry");
  }
}
