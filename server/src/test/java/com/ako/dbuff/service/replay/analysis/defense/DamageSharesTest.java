package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.DamageType;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DamageSharesTest {
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final Map<String, TeamSide> TEAMS =
      Map.of(
          "npc_dota_hero_axe", TeamSide.RADIANT,
          "npc_dota_hero_zeus", TeamSide.RADIANT,
          "npc_dota_hero_pudge", TeamSide.DIRE);
  private static final Function<String, DamageType> ABILITIES =
      name -> name.equals("zeus_arc_lightning") ? DamageType.MAGICAL : DamageType.UNKNOWN;

  private static CombatEvent hit(
      double second,
      String attacker,
      String target,
      String inflictor,
      int value,
      int flags,
      DamageType type) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        attacker,
        target,
        inflictor,
        flags,
        value,
        500,
        0,
        0,
        0,
        0,
        0,
        type);
  }

  private static DamageShares shares(List<CombatEvent> events, double to, double window) {
    return DamageShares.of(events, TEAMS, TeamSide.RADIANT, to, window, ABILITIES);
  }

  @Test
  void allPhysicalWindowGivesOne() {
    List<CombatEvent> events =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "",
                100,
                HEROES,
                DamageType.PHYSICAL),
            hit(
                20,
                "npc_dota_hero_pudge",
                "npc_dota_hero_zeus",
                "",
                50,
                HEROES,
                DamageType.PHYSICAL));

    DamageShares s = shares(events, 60, 300);

    assertThat(s.physical()).isEqualTo(1.0);
    assertThat(s.magical()).isZero();
    assertThat(s.pure()).isZero();
  }

  @Test
  void windowKeepsOnlyTheTrailingPart() {
    List<CombatEvent> events =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "",
                300,
                HEROES,
                DamageType.PHYSICAL),
            hit(
                200,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "x",
                100,
                HEROES,
                DamageType.MAGICAL),
            hit(
                400,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "x",
                900,
                HEROES,
                DamageType.PURE));

    DamageShares s = shares(events, 300, 150);

    assertThat(s.physical()).isZero();
    assertThat(s.magical()).isEqualTo(1.0);
  }

  @Test
  void illusionsCreepsAndSameTeamAreIgnored() {
    List<CombatEvent> events =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "",
                100,
                HEROES,
                DamageType.PHYSICAL),
            hit(
                11,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "",
                900,
                HEROES | CombatEvent.ATTACKER_ILLUSION,
                DamageType.MAGICAL),
            hit(
                12,
                "npc_dota_creep_badguys_melee",
                "npc_dota_hero_axe",
                "",
                900,
                CombatEvent.TARGET_HERO,
                DamageType.MAGICAL),
            hit(13, "npc_dota_hero_zeus", "npc_dota_hero_axe", "x", 900, HEROES, DamageType.PURE),
            hit(14, "npc_dota_hero_axe", "npc_dota_hero_pudge", "x", 900, HEROES, DamageType.PURE));

    DamageShares s = shares(events, 60, 300);

    assertThat(s.physical()).isEqualTo(1.0);
  }

  @Test
  void unknownTypeIsLeftOutAndNoKnownDamageGivesNull() {
    List<CombatEvent> unknownOnly =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "mystery",
                100,
                HEROES,
                DamageType.UNKNOWN));
    List<CombatEvent> mixed =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "mystery",
                700,
                HEROES,
                DamageType.UNKNOWN),
            hit(11, "npc_dota_hero_pudge", "npc_dota_hero_axe", "", 100, HEROES, DamageType.PURE));

    assertThat(shares(unknownOnly, 60, 300)).isNull();
    assertThat(shares(mixed, 60, 300).pure()).isEqualTo(1.0);
  }

  @Test
  void fallbackReadsEmptyInflictorAsPhysicalAndAbilitiesByTheirType() {
    List<CombatEvent> events =
        List.of(
            hit(
                10,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "",
                100,
                HEROES,
                DamageType.UNKNOWN),
            hit(
                11,
                "npc_dota_hero_pudge",
                "npc_dota_hero_axe",
                "zeus_arc_lightning",
                300,
                HEROES,
                DamageType.UNKNOWN));

    DamageShares s = shares(events, 60, 300);

    assertThat(s.physical()).isCloseTo(0.25, within(1e-9));
    assertThat(s.magical()).isCloseTo(0.75, within(1e-9));
  }
}
