package com.ako.dbuff.service.replay.visionloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff;
import com.ako.dbuff.service.replay.visionloop.VisionLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionLoopReportTest {
  private static FightVisionDomain row(String team, int observers, String verdict) {
    return FightVisionDomain.builder()
        .fightIndex(5)
        .team(team)
        .windowStart(1293.0)
        .windowEnd(1415.0)
        .activeObservers(observers)
        .activeSentries(0)
        .youngestObserverAge(observers == 0 ? null : 72)
        .lateWard(observers == 1)
        .lateWardSeconds(observers == 1 ? 18 : null)
        .wardsDestroyed(2)
        .destroyedBy("128")
        .invisibleHero(true)
        .invisibleHeroes("1,128")
        .detection(true)
        .detectionUsed(false)
        .verdict(verdict)
        .build();
  }

  private static MatchData data() {
    LostFight lost =
        new LostFight(
            FightDomain.builder().fightIndex(5).startSeconds(1383.0).endSeconds(1400.0).build(),
            FightExplanationDomain.builder().fightIndex(5).losingTeam("DIRE").build(),
            List.of(),
            0);
    WardEventDomain unknown =
        WardEventDomain.builder()
            .wardKind(WardKind.OBSERVER)
            .placedTime(100L)
            .removalKind(WardRemovalKind.UNKNOWN)
            .build();
    return new MatchData(
        9L,
        MatchDomain.builder().id(9L).radiantWin(true).duration(2000L).build(),
        "Ability Draft",
        List.of(lost),
        List.of(
            row("RADIANT", 1, "LATE_WARD"),
            row("DIRE", 0, "NO_VISION,INVISIBLE_HERO_NO_DETECTION")),
        Map.of(1L, "Mirana", 128L, "Windrunner"),
        List.of(unknown));
  }

  @Test
  void rowsAreInPlainWords_withTheEmptyVerdictColumnsLast() {
    String report = VisionLoopReport.render(data());

    assertThat(report)
        .contains("**Summary:** 1 important fights, 2 team rows:")
        .contains("no vision near the fight 1")
        .contains("ward placed just before the fight 1")
        .contains(
            "| 21:33 to 23:35 | Radiant, fight 5 at 23:03 | Dire | 1 observer, youngest 1:12 old |"
                + " yes, 18 s before | Mirana, Windrunner | available, not used | 2 by Windrunner |"
                + " ward placed just before the fight |  |  |")
        .contains("| no observer | no |")
        .contains("no vision near the fight; invisible hero without detection |  |  |")
        .doesNotContain("npc_dota_hero")
        .doesNotContain("INVISIBLE_HERO");
  }

  @Test
  void theLimitsTellWardsWithNoLoggedRemoval() {
    assertThat(VisionLoopReport.render(data()))
        .contains("## What the data cannot tell")
        .contains("1 wards, 1 with no removal in the log")
        .contains("were still within their life when the match ended");
  }

  @Test
  void theTableIsComparedBetweenRounds_keyedByWindowAndTeam() {
    String before = VisionLoopReport.render(data());
    String after = before.replace("| yes, 18 s before |", "| no |");

    assertThat(ReviewLoopDiff.changes(before, after, VisionLoopTopic.SECTIONS)).hasSize(1);
  }

  @Test
  void aMatchWithoutRowsSaysSo() {
    MatchData d = data();
    String report =
        VisionLoopReport.render(
            new MatchData(9L, d.match(), d.modeName(), List.of(), List.of(), Map.of(), List.of()));

    assertThat(report).contains("no ward log stored, so no vision rows.");
  }
}
