package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvisibilityLogTest {
  private static final InvisibleHeroes SOURCES =
      new InvisibleHeroFile(
          new ByteArrayInputStream(
              ("{\"heroes\": {\"npc_dota_hero_clinkz\": {\"ability\": \"clinkz_wind_walk\","
                      + " \"mode\": \"ACTIVE\"}, \"npc_dota_hero_riki\": {\"ability\":"
                      + " \"riki_permanent_invisibility\", \"mode\": \"PASSIVE\"}}, \"items\":"
                      + " [\"item_glimmer_cape\"]}")
                  .getBytes()),
          "test");
  private static final Map<String, Long> SLOTS =
      Map.of("npc_dota_hero_clinkz", 1L, "npc_dota_hero_riki", 2L, "npc_dota_hero_axe", 128L);
  private static final Set<Long> ALL = Set.of(1L, 2L, 128L);

  private static CombatEvent event(CombatEventKind kind, double at, String actor, String what) {
    return new CombatEvent(kind, at, actor, actor, what, 3, 0, 0, 0, 0, 0, 0, 0);
  }

  private static InvisibilityLog log(CombatEvent... events) {
    return new InvisibilityLog(List.of(events), SLOTS, SOURCES);
  }

  @Test
  void aModifierSpanOverlappingTheFightMakesTheHeroInvisible() {
    InvisibilityLog log =
        log(
            event(CombatEventKind.MODIFIER_ADD, 90, "npc_dota_hero_axe", "modifier_invisible"),
            event(CombatEventKind.MODIFIER_REMOVE, 105, "npc_dota_hero_axe", "modifier_invisible"));

    assertThat(log.invisibleIn(ALL, 100, 120, 15, Map.of())).containsExactly(128L);
    assertThat(log.invisibleIn(ALL, 106, 120, 15, Map.of())).isEmpty();
  }

  @Test
  void aRefreshedModifierStaysOneSpan_andAnOpenSpanRunsToTheEnd() {
    InvisibilityLog log =
        log(
            event(CombatEventKind.MODIFIER_ADD, 10, "npc_dota_hero_axe", "modifier_invisible"),
            event(CombatEventKind.MODIFIER_ADD, 20, "npc_dota_hero_axe", "modifier_invisible"),
            event(CombatEventKind.MODIFIER_REMOVE, 30, "npc_dota_hero_axe", "modifier_invisible"));

    assertThat(log.invisibleIn(ALL, 40, 50, 0, Map.of())).containsExactly(128L);
  }

  @Test
  void aListedAbilityOrItemUsedJustBeforeOrDuringTheFightCounts() {
    InvisibilityLog log =
        log(
            event(CombatEventKind.ABILITY, 95, "npc_dota_hero_clinkz", "clinkz_wind_walk"),
            event(CombatEventKind.ITEM, 60, "npc_dota_hero_axe", "item_glimmer_cape"),
            event(CombatEventKind.ABILITY, 99, "npc_dota_hero_axe", "axe_culling_blade"));

    assertThat(log.invisibleIn(ALL, 100, 120, 15, Map.of())).containsExactly(1L);
  }

  @Test
  void theOwnerOfAPassiveInvisibilityIsInvisibleInEveryFight() {
    InvisibilityLog log = log();

    assertThat(
            log.invisibleIn(
                ALL,
                100,
                120,
                15,
                Map.of(2L, Set.of("riki_permanent_invisibility"), 1L, Set.of("clinkz_wind_walk"))))
        .containsExactly(2L);
  }

  @Test
  void onlyTheSlotsAskedAboutAreTold() {
    InvisibilityLog log =
        log(event(CombatEventKind.ABILITY, 95, "npc_dota_hero_clinkz", "clinkz_wind_walk"));

    assertThat(log.invisibleIn(Set.of(128L), 100, 120, 15, Map.of())).isEmpty();
  }
}
