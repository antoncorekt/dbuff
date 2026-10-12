package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.fightloop.FightLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FightLoopReportTest {
  private static LostFight lost(int index, Integer swing, String loser, Long gold, String code) {
    FightDomain fight =
        FightDomain.builder().fightIndex(index).startSeconds(1383.0).endSeconds(1400.0).build();
    FightExplanationDomain explanation =
        FightExplanationDomain.builder()
            .fightIndex(index)
            .spikeIndex(swing)
            .losingTeam(loser)
            .goldLost(gold)
            .reasonCount(1)
            .build();
    FightReasonDomain reason =
        FightReasonDomain.builder()
            .code(code)
            .scope("PLAYER")
            .subjectSlot(128L)
            .team(loser)
            .severity(0.7)
            .evidence("no | teleport\nat start")
            .build();
    return new LostFight(fight, explanation, List.of(new RankedReason(1, reason)), 1);
  }

  private static MatchData data(MatchExplanation explanation) {
    return new MatchData(
        9L,
        MatchDomain.builder().id(9L).radiantWin(true).build(),
        "Ability Draft",
        explanation,
        List.of(ReplayPlayerDomain.builder().playerSlot(128L).heroEntityName("npc_x").build()),
        List.of(
            TeamfightDomain.builder()
                .fightIndex(2)
                .startTime(1380L)
                .endTime(1410L)
                .deaths(4L)
                .build()),
        List.of(
            TeamfightPlayerDomain.builder().fightIndex(2).playerSlot(128L).goldDelta(-700L).build(),
            TeamfightPlayerDomain.builder().fightIndex(2).playerSlot(129L).goldDelta(-300L).build(),
            TeamfightPlayerDomain.builder().fightIndex(2).playerSlot(1L).goldDelta(900L).build()));
  }

  private static final GameSpikeDomain SWING =
      GameSpikeDomain.builder()
          .spikeIndex(0)
          .startSeconds(1320)
          .endSeconds(1440)
          .benefitingTeam("RADIANT")
          .deltaGold(3200L)
          .deltaPercent(4.5)
          .primaryCause("FIGHT_WIN")
          .build();

  @Test
  void printsTheLostFightWithTheOpenDotaFactsAndTheRankedReasonWithEmptyVerdictColumns() {
    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(
                        new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1200L, "NO_TP")))),
                    List.of())));

    assertThat(report)
        .contains("| 0 | 5 | 23:03 | DIRE | 1200 | ? | 1 | #2 | 4 | -1000 |")
        .contains("| 0 | 5 | NO_TP | slot 128 (npc_x) | 1 | 0.70 | no \\| teleport at start | | |")
        .contains("### Swing 0, 22:00 to 24:00, 3200 gold");
  }

  @Test
  void aFightOutsideEverySwingKeepsAPlaceholderSwingAndNoOpenDotaMatchSaysNone() {
    MatchData noOd =
        data(new MatchExplanation(List.of(), List.of(lost(5, null, "DIRE", null, "X"))));
    noOd =
        new MatchData(
            noOd.matchId(),
            noOd.match(),
            noOd.modeName(),
            noOd.explanation(),
            noOd.replayPlayers(),
            List.of(),
            List.of());

    assertThat(FightLoopReport.render(noOd))
        .contains("| - | 5 | 23:03 | DIRE | ? | ? | 1 | none | none | none |");
  }

  @Test
  void theTwoDiffedTablesCanBeComparedBetweenRounds() {
    MatchExplanation first =
        new MatchExplanation(
            List.of(new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1200L, "NO_TP")))),
            List.of());
    MatchExplanation second =
        new MatchExplanation(
            List.of(new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1500L, "NO_TP")))),
            List.of());

    List<String> changes =
        ReviewLoopDiff.changes(
            FightLoopReport.render(data(first)),
            FightLoopReport.render(data(second)),
            FightLoopTopic.SECTIONS);

    assertThat(changes).containsExactly("fight 0 5, gold lost: 1200 -> 1500");
  }

  @Test
  void ofTwoOverlappingOpenDotaFightsTheOneWithTheMostOverlapIsShown() {
    MatchData d = data(new MatchExplanation(List.of(), List.of(lost(5, null, "DIRE", null, "X"))));
    d =
        new MatchData(
            d.matchId(),
            d.match(),
            d.modeName(),
            d.explanation(),
            d.replayPlayers(),
            List.of(
                TeamfightDomain.builder()
                    .fightIndex(1)
                    .startTime(1370L)
                    .endTime(1380L)
                    .deaths(3L)
                    .build(),
                TeamfightDomain.builder()
                    .fightIndex(2)
                    .startTime(1385L)
                    .endTime(1400L)
                    .deaths(4L)
                    .build()),
            List.of());

    assertThat(FightLoopReport.render(d)).contains("| #2 | 4 | ? |");
  }

  @Test
  void aMatchWithNoLostFightsSaysSo() {
    assertThat(FightLoopReport.render(data(new MatchExplanation(List.of(), List.of()))))
        .contains("No lost fights stored");
  }
}
