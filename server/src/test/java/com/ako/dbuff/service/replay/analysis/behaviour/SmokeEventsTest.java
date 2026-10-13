package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeEventsTest {
  private static final Map<String, Long> SLOTS =
      Map.of("npc_dota_hero_axe", 0L, "npc_dota_hero_lion", 1L, "npc_dota_hero_mirana", 128L);
  private static final int HERO = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  private static CombatEvent event(
      CombatEventKind kind, double second, String attacker, String target, String inflictor) {
    return new CombatEvent(kind, second, attacker, target, inflictor, HERO, 0, 0, 0, 4500, 0, 0, 0);
  }

  private static CombatEvent modifier(CombatEventKind kind, double second, String target) {
    return event(kind, second, "npc_dota_hero_axe", target, SmokeEvents.MODIFIER);
  }

  @Test
  void uses_areTheItemEventsOfKnownHeroes() {
    List<CombatEvent> events =
        List.of(
            event(CombatEventKind.ITEM, 10, "npc_dota_hero_axe", "", SmokeEvents.ITEM),
            event(CombatEventKind.ITEM, 11, "npc_dota_hero_axe", "", "item_blink"),
            event(CombatEventKind.ITEM, 12, "npc_dota_hero_unknown", "", SmokeEvents.ITEM));

    assertThat(SmokeEvents.uses(events, SLOTS)).containsExactly(new SmokeEvents.Use(0, 10));
  }

  @Test
  void holds_pairEachAddWithTheNextRemovalOfTheSameHeroAndKeepTheDuration() {
    List<CombatEvent> events =
        List.of(
            modifier(CombatEventKind.MODIFIER_ADD, 10, "npc_dota_hero_axe"),
            modifier(CombatEventKind.MODIFIER_ADD, 10, "npc_dota_hero_lion"),
            modifier(CombatEventKind.MODIFIER_REMOVE, 20, "npc_dota_hero_lion"),
            modifier(CombatEventKind.MODIFIER_REMOVE, 55, "npc_dota_hero_axe"),
            modifier(CombatEventKind.MODIFIER_ADD, 300, "npc_dota_hero_lion"));

    assertThat(SmokeEvents.holds(events, SLOTS))
        .containsExactlyInAnyOrder(
            new SmokeHold(0, 10, 55.0, 45),
            new SmokeHold(1, 10, 20.0, 45),
            new SmokeHold(1, 300, null, 45));
  }

  @Test
  void roshanKills_goToTheTeamOfTheHeroWhoStruckTheLastBlow() {
    List<CombatEvent> events =
        List.of(
            event(CombatEventKind.DEATH, 900, "npc_dota_hero_mirana", "npc_dota_roshan", ""),
            event(CombatEventKind.DEATH, 901, "npc_dota_hero_mirana", "npc_dota_hero_axe", ""),
            event(CombatEventKind.DEATH, 902, "npc_dota_roshan", "npc_dota_hero_axe", ""));

    assertThat(SmokeEvents.roshanKills(events, SLOTS))
        .isEqualTo(Map.of(TeamSide.DIRE, List.of(900.0)));
  }
}
