package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.fightloop.FightLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff;
import java.util.List;
import java.util.Map;
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
    return new LostFight(
        fight, explanation, List.of(new RankedReason(1, reason, List.of(), true)), 1);
  }

  private static MatchData data(MatchExplanation explanation) {
    return new MatchData(
        9L,
        MatchDomain.builder().id(9L).radiantWin(true).build(),
        "Ability Draft",
        explanation,
        Map.of(128L, "Windrunner"),
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
  void printsTheLostFightInPlainWordsWithTheOpenDotaFactsAndTheRankedReasonWithEmptyVerdicts() {
    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(
                        new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1200L, "NO_TP")))),
                    List.of())));

    assertThat(report)
        .contains("| fight | main reason | gold lost | swing | OpenDota teamfight |")
        .contains(
            "| Dire lost fight 5 at 23:03 | Windrunner had no teleport | 1.2k | swing 0 |"
                + " 4 deaths, -1k gold change for the loser |")
        .contains("| reason | fight | rank | also | severity | assistant | owner |")
        .contains("| Windrunner had no teleport | Dire lost fight 5 at 23:03 | 1 |  | 0.70 | | |")
        .contains("### Swing 0, 22:00 to 24:00: Radiant gained 3.2k gold (4.5%), cause: fight win")
        .contains("**How to read this:**")
        .contains("**Summary:** 1 lost fight (Dire lost 1); every one has a reason;");
  }

  @Test
  void noTableCellShowsAStoredCodeOrAHeroEntityName() {
    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(
                        new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1200L, "NO_TP")))),
                    List.of())));

    assertThat(report.lines().filter(l -> l.startsWith("|")).toList())
        .noneMatch(l -> l.contains("NO_TP") || l.contains("DIRE") || l.contains("npc_dota"));
  }

  @Test
  void theCodeAndTheEvidenceStayUnderTheNotes() {
    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(
                        new SwingExplanation(SWING, List.of(lost(5, 0, "DIRE", 1200L, "NO_TP")))),
                    List.of())));

    assertThat(report)
        .contains(
            "- fight 5, Windrunner had no teleport: code NO_TP, evidence: no | teleport at start");
  }

  @Test
  void aFightOutsideEverySwingKeepsAPlaceholderSwingAndNoOpenDotaMatchSaysNone() {
    MatchData noOd =
        data(new MatchExplanation(List.of(), List.of(lost(5, null, "DIRE", null, "NO_TP"))));
    noOd =
        new MatchData(
            noOd.matchId(),
            noOd.match(),
            noOd.modeName(),
            noOd.explanation(),
            noOd.heroNames(),
            List.of(),
            List.of());

    assertThat(FightLoopReport.render(noOd))
        .contains("| Dire lost fight 5 at 23:03 | Windrunner had no teleport | ? | none | none |");
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

    assertThat(changes).containsExactly("Dire lost fight 5 at 23:03, gold lost: 1.2k -> 1.5k");
  }

  @Test
  void ofTwoOverlappingOpenDotaFightsTheOneWithTheMostOverlapIsShown() {
    MatchData d =
        data(new MatchExplanation(List.of(), List.of(lost(5, null, "DIRE", null, "NO_TP"))));
    d =
        new MatchData(
            d.matchId(),
            d.match(),
            d.modeName(),
            d.explanation(),
            d.heroNames(),
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

    assertThat(FightLoopReport.render(d)).contains("| 4 deaths, ? gold change for the loser |");
  }

  @Test
  void anAbsenceEntryListsItsAlsoReasons_andAnEarlyCooldownReasonIsMarkedNotRanked() {
    LostFight base = lost(5, 0, "DIRE", 1200L, "NO_TP");
    FightReasonDomain cooldown =
        FightReasonDomain.builder()
            .code("ABILITY_ON_COOLDOWN")
            .scope("PLAYER")
            .subjectSlot(128L)
            .team("DIRE")
            .severity(0.4)
            .evidence("e")
            .build();
    LostFight fight =
        new LostFight(
            base.fight(),
            base.explanation(),
            List.of(
                new RankedReason(
                    1,
                    base.reasons().get(0).reason(),
                    List.of("TP_ON_COOLDOWN", "SUPPORT_ABSENT"),
                    true),
                new RankedReason(0, cooldown, List.of(), false)),
            2);

    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(new SwingExplanation(SWING, List.of(fight))), List.of())));

    assertThat(report)
        .contains(
            "| Windrunner had no teleport | Dire lost fight 5 at 23:03 | 1 | had the teleport on"
                + " cooldown, was not in the fight | 0.70 |")
        .contains(
            "| Windrunner had E on cooldown | Dire lost fight 5 at 23:03 | not ranked"
                + " (early fight) |  | 0.40 |")
        .contains("Windrunner had no teleport (and 1 more)");
  }

  @Test
  void aMatchWithNoLostFightsSaysSo() {
    assertThat(FightLoopReport.render(data(new MatchExplanation(List.of(), List.of()))))
        .contains("No lost fights stored");
  }
}
