package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.behaviourloop.BehaviourLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviourLoopReportTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Axe", 128L, "Mirana");

  private static TeamRotationDomain.TeamRotationDomainBuilder rotation(long slot, int start) {
    return TeamRotationDomain.builder()
        .team(slot >= 128 ? "DIRE" : "RADIANT")
        .playerSlot(slot)
        .startSeconds(start)
        .endSeconds(start + 30)
        .endReason("REJOINED")
        .fromZone("LANE")
        .fromLane("MID")
        .toZone("LANE")
        .toLane("TOP")
        .toSide("ENEMY")
        .groupZone("LANE")
        .groupLane("TOP")
        .kills(0)
        .creepKills(0)
        .fights(0)
        .fightsWon(0)
        .verdict("NEUTRAL");
  }

  private static MatchData data(List<TeamRotationDomain> rows) {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    return new MatchData(
        1,
        match,
        "Ability Draft",
        HEROES,
        rows,
        List.of(
            FightDomain.builder()
                .fightIndex(3)
                .startSeconds(760.0)
                .endSeconds(790.0)
                .zone("LANE")
                .lane("TOP")
                .build()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of(),
        new BehaviourProperties());
  }

  @Test
  void render_tellsARotationInPlainWordsWithTheVerdictLast() {
    String report =
        BehaviourLoopReport.render(
            data(
                List.of(
                    rotation(128, 730)
                        .fights(1)
                        .fightsWon(1)
                        .fightIndex(3)
                        .kills(2)
                        .creepKills(4)
                        .laneLeft("MID")
                        .proxyOwnKills(1)
                        .proxyEnemyKills(5)
                        .teleported(true)
                        .verdict("GOOD")
                        .build())));

    assertThat(report)
        .contains("**How to read this:**")
        .contains(
            "**Summary:** rotations 1 (Radiant 0, Dire 1): 1 good, 0 wasted, 0 neutral, 0 died; 1"
                + " arrived by teleport; reasons: died on the trip 0, did nothing there 0, farm"
                + " dropped 0, arrived after the fight was over 0, left the lane undefended 0, an"
                + " own tower fell while he was away 0.")
        .contains("Dire Mirana at 12:10")
        .contains(
            "left mid lane (the group was in top lane), got to top lane on the enemy half at 12:40,"
                + " away 30 s (teleported); joined the group there")
        .contains("1 fight, 1 won (at 12:40 in top lane), 2 kills or assists, 4 creeps")
        .contains("mid lane: his partners 1 creeps, the enemy 5")
        .contains("| good | nothing to add | |")
        .doesNotContain("GOOD")
        .doesNotContain("128");
  }

  @Test
  void render_aRotationWithNothingAndNoLaneSaysSo() {
    String report =
        BehaviourLoopReport.render(
            data(List.of(rotation(0, 600).verdict("DIED").endReason("DIED").build())));

    assertThat(report)
        .contains("Radiant Axe at 10:00")
        .contains("| nothing | not laning then (no lane, or the laning was over) | died |")
        .contains("died before the trip ended");
  }

  @Test
  void render_listsEveryReasonInPlainWordsWithTheNumbers() {
    String report =
        BehaviourLoopReport.render(
            data(
                List.of(
                    rotation(0, 900)
                        .reasons(
                            "DID_NOTHING,INCOME_DROP,ARRIVED_AFTER_FIGHT_ENDED,"
                                + "LEFT_LANE_UNDEFENDED,TOWER_LOST_WHILE_AWAY")
                        .incomeBefore(1240)
                        .incomeDuring(410)
                        .xpBefore(620)
                        .xpDuring(300)
                        .laneLeft("MID")
                        .proxyOwnKills(2)
                        .proxyEnemyKills(6)
                        .verdict("WASTED")
                        .build())));

    assertThat(report)
        .contains(
            "Axe did nothing there (no fight, no damage to enemy heroes or towers, no kill, no"
                + " creeps)")
        .contains(
            "Axe's farm dropped from 1.2k to 410 gold per minute (experience from 620 to 300 per"
                + " minute)")
        .contains("Axe arrived after the fight in that lane was over")
        .contains("the lane Axe left lost the creep count (his partners 2, the enemy 6)")
        .contains("an own tower fell while Axe was away")
        .contains("farm dropped 1")
        .doesNotContain("INCOME_DROP");
  }

  @Test
  void render_noRotationsIsASummaryLine() {
    assertThat(BehaviourLoopReport.render(data(List.of())))
        .contains("**Summary:** no rotations stored");
  }

  private static MatchData withSections(
      List<TeamSplitStretchDomain> stretches,
      List<TeamLaneSwapDomain> swaps,
      Map<Long, Integer> roles) {
    MatchData base = data(List.of());
    return new MatchData(
        base.matchId(),
        base.match(),
        base.modeName(),
        base.heroNames(),
        base.rotations(),
        base.fights(),
        stretches,
        swaps,
        base.smokes(),
        base.objectives(),
        roles,
        base.properties());
  }

  @Test
  void render_tellsAStretchInPlainWordsWithTheVerdictColumnEmptyAndLast() {
    String report =
        BehaviourLoopReport.render(
            withSections(
                List.of(
                    TeamSplitStretchDomain.builder()
                        .team("RADIANT")
                        .startSeconds(1080)
                        .endSeconds(1410)
                        .label("SPLIT")
                        .towersLost(2)
                        .towersTaken(0)
                        .ownHeroesDied(1)
                        .enemyHeroesDied(0)
                        .build()),
                List.of(),
                Map.of()));

    assertThat(report)
        .contains("## Group or split")
        .contains("| stretch | how the team stood | what happened | owner verdict |")
        .contains("| Radiant 18:00 to 23:30 | split up | lost 2 towers, 1 of its heroes died | |")
        .contains("**Summary:** no rotations stored")
        .contains("1 stretches (0 together, 1 split up, 0 mixed); no lane swaps.")
        .doesNotContain("SPLIT");
  }

  @Test
  void render_tellsALaneSwapWithTheHeroTheRoleAndTheClock() {
    String report =
        BehaviourLoopReport.render(
            withSections(
                List.of(),
                List.of(
                    TeamLaneSwapDomain.builder()
                        .team("DIRE")
                        .playerSlot(128L)
                        .laneBefore("TOP")
                        .laneAfter("MID")
                        .fromSeconds(540)
                        .toSeconds(715)
                        .build()),
                Map.of(128L, 3)));

    assertThat(report)
        .contains("## Lane swaps")
        .contains(
            "| Dire Mirana at 9:00 | Dire moved Mirana (3 offlane) from the top lane to the mid"
                + " lane at 9:00 (in it until 11:55) | |")
        .contains("1 lane swap.")
        .doesNotContain("TOP");
  }

  @Test
  void render_tellsASmokeInPlainWordsWithTheVerdictColumnEmptyAndLast() {
    MatchData base = data(List.of());
    MatchData withSmoke =
        new MatchData(
            base.matchId(),
            base.match(),
            base.modeName(),
            base.heroNames(),
            base.rotations(),
            base.fights(),
            base.stretches(),
            base.swaps(),
            List.of(
                SmokeUseDomain.builder()
                    .team("RADIANT")
                    .userSlot(0L)
                    .groupSlots("0,128")
                    .startSeconds(1431)
                    .endSeconds(1440)
                    .endReason("BROKEN")
                    .startZone("LANE")
                    .startLane("MID")
                    .endZone("RIVER")
                    .outcome("PAID_OFF")
                    .kills(2)
                    .buildings(1)
                    .roshanKills(0)
                    .groupDeaths(1)
                    .enemyDeaths(2)
                    .build()),
            base.objectives(),
            base.roles(),
            base.properties());

    String report = BehaviourLoopReport.render(withSmoke);

    assertThat(report)
        .contains("## Smoke")
        .contains(
            "| Radiant Axe at 23:51 | Axe, Mirana | 23:51 to 24:00 (9 s), the smoke broke | from"
                + " the mid lane to the river | paid off | killed or helped kill 2 enemy heroes, 1"
                + " enemy tower or barracks fell, 1 of the group died | |")
        .contains(
            "Smoke: 1 use (Radiant 1, Dire 0): 1 paid off, 0 came to nothing, 0 backfired; 0"
                + " ran out, 1 broke, 0 not known.")
        .doesNotContain("PAID_OFF");
  }

  @Test
  void render_noSmokeIsASummaryPhrase() {
    assertThat(BehaviourLoopReport.render(data(List.of())))
        .contains("Smoke: no smoke uses stored.");
  }

  private static MatchData withObjectives(List<ObjectiveConversionDomain> objectives) {
    MatchData base = data(List.of());
    return new MatchData(
        base.matchId(),
        base.match(),
        base.modeName(),
        base.heroNames(),
        base.rotations(),
        base.fights(),
        base.stretches(),
        base.swaps(),
        base.smokes(),
        objectives,
        base.roles(),
        base.properties());
  }

  @Test
  void render_tellsAMissedObjectiveWithTheNearestTowerAndTheDistance() {
    String report =
        BehaviourLoopReport.render(
            withObjectives(
                List.of(
                    ObjectiveConversionDomain.builder()
                        .fightIndex(3)
                        .winnerTeam("RADIANT")
                        .result("MISSED")
                        .winnersAlive(3)
                        .nearestTower("dota_badguys_tower2_mid")
                        .towerLane("MID")
                        .towerDistance(3400)
                        .nearestHeroSlot(0L)
                        .heroZone("RIVER")
                        .build())));

    assertThat(report)
        .contains("## Objectives after fights")
        .contains("| fight | who won | result | what happened | owner verdict |")
        .contains(
            "| Fight at 12:40 to 13:10 | Radiant | missed | Radiant took nothing in 90 s; nearest"
                + " enemy tower still standing: tier 2 in the mid lane, 3400 units from Axe (river)"
                + " at the end of the fight, 3 of the winners alive | |")
        .contains(
            "Objectives: 1 won important fight: 0 converted (0 a tower, 0 barracks, 0 the ancient,"
                + " 0 Roshan), 1 missed.")
        .doesNotContain("MISSED");
  }

  @Test
  void render_tellsATowerAndARoshanKillInPlainWords() {
    String report =
        BehaviourLoopReport.render(
            withObjectives(
                List.of(
                    ObjectiveConversionDomain.builder()
                        .fightIndex(3)
                        .winnerTeam("DIRE")
                        .result("CONVERTED")
                        .objectiveKind("TOWER")
                        .objectiveKey("npc_dota_goodguys_tower1_top")
                        .secondsAfter(30)
                        .build(),
                    ObjectiveConversionDomain.builder()
                        .fightIndex(4)
                        .winnerTeam("RADIANT")
                        .result("CONVERTED")
                        .objectiveKind("ROSHAN")
                        .objectiveKey("npc_dota_roshan")
                        .secondsAfter(0)
                        .roshanNearSlots("0")
                        .roshanCall("GAMBLE")
                        .build())));

    assertThat(report)
        .contains(
            "| converted | Dire took the tier 1 tower in the top lane 30 s after it ended | |")
        .contains(
            "| converted | Radiant killed Roshan during the fight; Axe stood at the pit; a gamble,"
                + " the winner was behind | |");
  }

  @Test
  void render_noObjectivesIsASummaryPhrase() {
    assertThat(BehaviourLoopReport.render(data(List.of())))
        .contains("Objectives: no objective conversions stored.");
  }
}
