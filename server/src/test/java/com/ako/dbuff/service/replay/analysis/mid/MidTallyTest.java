package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MidTallyTest {
  private static final Map<String, Integer> PLAYERS = Map.of("puck", 0, "lina", 5);

  private static CombatEvent death(
      double second, String killer, String victim, int flags, int assists) {
    return new CombatEvent(
        CombatEventKind.DEATH, second, killer, victim, "", flags, 0, 0, 0, 0, 0, 0, assists);
  }

  private static final int HERO_KILL = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  @Test
  void aHeroKillCountsForTheKillerAndEachAssistBitForItsPlayer() {
    MidTally tally =
        MidTally.of(
            List.of(
                death(100, "puck", "lina", HERO_KILL, 0b100010),
                death(200, "npc_dota_creep_badguys_melee", "puck", CombatEvent.TARGET_HERO, 0)),
            PLAYERS);

    assertThat(tally.kills()).containsOnlyKeys(0).containsEntry(0, List.of(100.0));
    assertThat(tally.assists()).containsOnlyKeys(1, 5);
  }

  @Test
  void anIllusionOrACreepDeathIsNotAHeroKill() {
    MidTally tally =
        MidTally.of(
            List.of(
                death(100, "puck", "lina", HERO_KILL | CombatEvent.ATTACKER_ILLUSION, 0),
                death(110, "puck", "npc_dota_creep", CombatEvent.ATTACKER_HERO, 0)),
            PLAYERS);

    assertThat(tally.kills()).isEmpty();
  }

  @Test
  void countsTimesInAHalfOpenRange() {
    List<Double> times = List.of(10.0, 20.0, 30.0);

    assertThat(MidTally.count(times, 10, 30)).isEqualTo(2);
    assertThat(MidTally.count(null, 0, 100)).isZero();
  }

  @Test
  void aKillOnHimselfOrOnATeammateIsNotAKill() {
    Map<String, Integer> players = Map.of("puck", 0, "lina", 5, "sniper", 1);

    MidTally tally =
        MidTally.of(
            List.of(
                death(100, "puck", "puck", HERO_KILL, 0b10),
                death(110, "puck", "sniper", HERO_KILL, 0b10),
                death(120, "puck", "lina", HERO_KILL, 0)),
            players);

    assertThat(tally.kills().get(0)).containsExactly(120.0);
    assertThat(tally.assists()).isEmpty();
  }
}
