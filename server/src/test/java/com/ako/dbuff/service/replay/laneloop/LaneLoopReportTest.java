package com.ako.dbuff.service.replay.laneloop;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LanePlayerStatDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.laneloop.LaneLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LaneLoopReportTest {
  private static final Map<Long, String> HEROES =
      Map.of(0L, "Axe", 1L, "Lich", 128L, "Mirana", 129L, "Doom", 2L, "Rubick");

  private static LaneAssignmentDomain assignment(
      long slot, String team, String position, String end) {
    return LaneAssignmentDomain.builder()
        .playerSlot(slot)
        .team(team)
        .lane("BOTTOM")
        .pairId(1)
        .position(position)
        .startSeconds(15)
        .endSeconds(500)
        .endReason(end)
        .build();
  }

  private static LaneResultDomain result(
      String team, String position, String result, double margin) {
    return LaneResultDomain.builder()
        .pairId(1)
        .lane("BOTTOM")
        .team(team)
        .position(position)
        .heroes(2)
        .enemyHeroes(2)
        .startSeconds(15)
        .endSeconds(520)
        .endReason("TOWER_FELL")
        .sideEndSeconds(500)
        .sideEndReason("JUNGLE")
        .creepScore(1234.0)
        .lastHits(30)
        .siegeLastHits(1)
        .denies(4)
        .xp(2400.0)
        .level(6.5)
        .kills(2)
        .deaths(1)
        .heroDamageDealt(900L)
        .ownTowerFell("DIRE".equals(team))
        .result(result)
        .margin(margin)
        .build();
  }

  private static MatchData data(List<LaneReasonDomain> reasons) {
    return new MatchData(
        9L,
        MatchDomain.builder().id(9L).radiantWin(true).radiantScore(10L).direScore(5L).build(),
        "Ability Draft",
        HEROES,
        List.of(
            assignment(0, "RADIANT", "SAFE", "JUNGLE"),
            assignment(1, "RADIANT", "SAFE", "TOWER_FELL"),
            assignment(128, "DIRE", "OFF", "TOWER_FELL"),
            assignment(129, "DIRE", "OFF", "TOWER_FELL"),
            LaneAssignmentDomain.builder().playerSlot(2L).team("RADIANT").build()),
        List.of(
            LanePlayerStatDomain.builder()
                .playerSlot(0L)
                .pairId(1)
                .lastHits(20)
                .meleeLastHits(12)
                .rangedLastHits(7)
                .siegeLastHits(1)
                .denies(3)
                .creepScore(900.0)
                .sideShare(0.73)
                .xp(1500.0)
                .level(6)
                .ultimateSeconds(460)
                .kills(1)
                .deaths(0)
                .build(),
            LanePlayerStatDomain.builder()
                .playerSlot(128L)
                .pairId(1)
                .lastHits(10)
                .meleeLastHits(10)
                .rangedLastHits(0)
                .siegeLastHits(0)
                .denies(1)
                .creepScore(300.0)
                .sideShare(0.5)
                .level(5)
                .kills(0)
                .deaths(1)
                .build()),
        List.of(result("RADIANT", "SAFE", "WON", 0.45), result("DIRE", "OFF", "LOST", -0.45)),
        reasons,
        6);
  }

  @Test
  void render_laysOutThreeTablesInWordsWithTheVerdictColumnsLast() {
    String report =
        LaneLoopReport.render(
            data(
                List.of(
                    LaneReasonDomain.builder()
                        .pairId(1)
                        .team("DIRE")
                        .rank(1)
                        .code("PARTNER_ABSENT")
                        .direction("HURT")
                        .subjectSlot(129L)
                        .severity(0.5)
                        .evidence("Doom was away 120 s of the 480 s his side laned")
                        .build())));

    assertThat(report)
        .contains("## Lanes", "## Players in the lane", "## Why")
        .contains(
            "**How to read this:**",
            "**Summary:** 1 lanes: bottom lane 8:40, Radiant won clearly (+0.45)")
        .contains(
            "| Radiant safe lane: Axe and Lich vs Dire off lane: Mirana and Doom | Radiant won"
                + " clearly (+0.45) |")
        .contains("the Dire lane tower fell")
        .contains("Radiant 1234 (30 last hits, 4 denies, 1 siege) vs Dire 1234")
        .contains(
            "| Axe | Radiant safe lane, 0:15 to 8:20 (went to farm the jungle) | 20 (12 melee,")
        .contains("73% of his side")
        .contains("Dire's Mirana did not reach level 6")
        .contains("did not reach level 6 when the lane ended (level 5)")
        .contains("reached level 6 at 7:40, before the lane ended")
        .contains("Radiant: the hero had the ultimate")
        .contains("| Rubick | no lane (roaming, jungling or not seen in a lane)")
        .contains(
            "| Dire off lane (lost) was hurt: Doom was away 120 s of the 480 s his side laned | 1 |"
                + " 0.50 | | |");
    assertThat(report)
        .doesNotContain("TOWER_FELL", "JUNGLE", "PARTNER_ABSENT", "RADIANT", "npc_dota")
        .contains("| assistant | owner |");
  }

  @Test
  void render_saysSoWhenNoLaneIsStored() {
    MatchData empty =
        new MatchData(
            9L,
            MatchDomain.builder().id(9L).build(),
            "Ability Draft",
            HEROES,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            6);

    assertThat(LaneLoopReport.render(empty)).contains("no lanes stored");
  }
}
