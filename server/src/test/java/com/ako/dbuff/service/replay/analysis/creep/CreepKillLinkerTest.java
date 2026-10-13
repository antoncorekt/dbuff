package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreepKillLinkerTest {

  private static final String LUNA = "npc_dota_hero_luna";
  private static final String AXE = "npc_dota_hero_axe";
  private static final Map<String, Zone.Half> HALVES =
      Map.of(LUNA, Zone.Half.RADIANT, AXE, Zone.Half.DIRE);
  private static final int CREEP_GOLD = 13;
  private static final int NEUTRAL_GOLD = 14;
  private static final int CREEP_XP = 2;

  @Test
  void link_takesTheGoldAndExperienceWrittenJustBeforeTheDeathRow() {
    List<CombatEvent> events =
        List.of(
            gold(10, LUNA, 38, CREEP_GOLD, 100, 200),
            xp(10, LUNA, 57),
            xp(10, AXE, 57),
            death(10, LUNA, "npc_dota_creep_badguys_melee"));

    List<CreepKill> kills = CreepKillLinker.link(events, HALVES);

    assertThat(kills).hasSize(1);
    CreepKill kill = kills.getFirst();
    assertThat(kill.creepClass()).isEqualTo(CreepClass.LANE_MELEE);
    assertThat(kill.side()).isEqualTo(ActivitySegment.Side.ENEMY);
    assertThat(kill.denied()).isFalse();
    assertThat(kill.gold()).isEqualTo(38);
    assertThat(kill.xp()).isEqualTo(57);
    assertThat(kill.xpRecipients()).isEqualTo(2);
    assertThat(kill.x()).isEqualTo(100);
    assertThat(kill.y()).isEqualTo(200);
  }

  @Test
  void link_marksAnOwnLaneCreepAsADenyWithoutGold() {
    List<CombatEvent> events = List.of(death(10, LUNA, "npc_dota_creep_goodguys_ranged"));

    CreepKill kill = CreepKillLinker.link(events, HALVES).getFirst();

    assertThat(kill.denied()).isTrue();
    assertThat(kill.side()).isEqualTo(ActivitySegment.Side.OWN);
    assertThat(kill.gold()).isNull();
    assertThat(kill.x()).isNull();
  }

  @Test
  void link_givesACampCreepNoSideAndReadsTheNeutralGold() {
    List<CombatEvent> events =
        List.of(
            gold(20, AXE, 60, NEUTRAL_GOLD, 5, 6), death(20, AXE, "npc_dota_neutral_centaur_khan"));

    CreepKill kill = CreepKillLinker.link(events, HALVES).getFirst();

    assertThat(kill.side()).isNull();
    assertThat(kill.denied()).isFalse();
    assertThat(kill.creepClass()).isEqualTo(CreepClass.NEUTRAL);
    assertThat(kill.gold()).isEqualTo(60);
  }

  @Test
  void link_keepsTwoKillsOfOneMomentApartByTheirOrder() {
    List<CombatEvent> events =
        List.of(
            gold(30, LUNA, 38, CREEP_GOLD, 0, 0),
            death(30, LUNA, "npc_dota_creep_badguys_melee"),
            gold(30, LUNA, 52, CREEP_GOLD, 0, 0),
            death(30, LUNA, "npc_dota_creep_badguys_ranged"));

    List<CreepKill> kills = CreepKillLinker.link(events, HALVES);

    assertThat(kills).extracting(CreepKill::gold).containsExactly(38, 52);
  }

  @Test
  void link_leavesTheGoldNullWhenTwoEntriesPrecedeOneKill() {
    List<CombatEvent> events =
        List.of(
            gold(30, LUNA, 38, CREEP_GOLD, 0, 0),
            gold(30, LUNA, 52, CREEP_GOLD, 0, 0),
            death(30, LUNA, "npc_dota_creep_badguys_melee"));

    assertThat(CreepKillLinker.link(events, HALVES).getFirst().gold()).isNull();
  }

  @Test
  void link_readsAFlagbearersBonusAfterItsDeathRow_butNotAKillsGoldThatFollows() {
    List<CombatEvent> flag =
        List.of(
            gold(40, LUNA, 38, CREEP_GOLD, 0, 0),
            death(40, LUNA, "npc_dota_creep_badguys_flagbearer"),
            gold(40, LUNA, 47, CREEP_GOLD, 0, 0),
            gold(40, AXE, 47, CREEP_GOLD, 0, 0));
    List<CombatEvent> flagThenMelee =
        List.of(
            gold(40, LUNA, 38, CREEP_GOLD, 0, 0),
            death(40, LUNA, "npc_dota_creep_badguys_flagbearer"),
            gold(40, LUNA, 38, CREEP_GOLD, 0, 0),
            death(40, LUNA, "npc_dota_creep_badguys_melee"));

    CreepKill first = CreepKillLinker.link(flag, HALVES).getFirst();
    List<CreepKill> second = CreepKillLinker.link(flagThenMelee, HALVES);

    assertThat(first.gold()).isEqualTo(38);
    assertThat(first.bonusGold()).isEqualTo(47);
    assertThat(second.getFirst().bonusGold()).isNull();
    assertThat(second.get(1).gold()).isEqualTo(38);
  }

  @Test
  void link_skipsSummonsHeroesAndBuildingsAsTargetsAndKillsByNonHeroes() {
    CombatEvent heroKill = death(50, LUNA, AXE);
    CombatEvent wolf = death(50, LUNA, "npc_dota_lycan_wolf1");
    CombatEvent tower = death(50, LUNA, "npc_dota_badguys_tower1_top");
    CombatEvent byCreep =
        new CombatEvent(
            CombatEventKind.DEATH,
            50,
            "npc_dota_creep_badguys_melee",
            "npc_dota_creep_goodguys_melee",
            "",
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0);

    assertThat(CreepKillLinker.link(List.of(heroKill, wolf, tower, byCreep), HALVES)).isEmpty();
  }

  private static CombatEvent death(double second, String attacker, String target) {
    int flags =
        CombatEvent.ATTACKER_HERO
            | (target.startsWith("npc_dota_hero") ? CombatEvent.TARGET_HERO : 0);
    return new CombatEvent(
        CombatEventKind.DEATH, second, attacker, target, "", flags, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent gold(double second, String hero, int value, int reason, int x, int y) {
    return new CombatEvent(
        CombatEventKind.GOLD, second, "", hero, "", 0, value, 0, reason, 0, x, y, 0);
  }

  private static CombatEvent xp(double second, String hero, int value) {
    return new CombatEvent(
        CombatEventKind.XP, second, "", hero, "", 0, value, 0, CREEP_XP, 0, 0, 0, 0);
  }
}
