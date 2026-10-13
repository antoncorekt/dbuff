package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
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
        .contains("Axe did nothing there (no damage to heroes or towers, no kill, no creeps)")
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
}
