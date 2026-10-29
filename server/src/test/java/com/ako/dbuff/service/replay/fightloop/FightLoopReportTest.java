package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeries;
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
        null,
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
        .contains(
            "| fight | important | gold change | XP change | share of net worth | flags | main"
                + " reason | gold lost | swing | OpenDota teamfight |")
        .contains(
            "| Dire lost fight 5 at 23:03 | unknown | ? | ? | ? | ? | Windrunner had no teleport |"
                + " 1.2k (deaths not priced: ?) | swing 0 | 4 deaths, -1k gold change for the loser"
                + " |")
        .contains("| reason | fight | rank | also | severity | assistant | owner |")
        .contains("| Windrunner had no teleport | Dire lost fight 5 at 23:03 | 1 |  | 0.70 | | |")
        .contains("### Swing 0, 22:00 to 24:00: Radiant gained 3.2k gold (4.5%), cause: fight win")
        .contains("**How to read this:**")
        .contains(
            "**Summary:** 1 decided fight, importance not known, no advantage series (Dire lost 1);"
                + " every one has a reason;");
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

    assertThat(
            report
                .lines()
                .takeWhile(l -> !l.startsWith("## " + FightLoopReport.NOTES))
                .filter(l -> l.startsWith("|"))
                .toList())
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
        .contains("| fight | reason | stored code | evidence |")
        .contains(
            "| Dire lost fight 5 at 23:03 | Windrunner had no teleport | NO_TP | no \\| teleport at"
                + " start |");
  }

  private static MatchExplanation single(LostFight... fights) {
    return new MatchExplanation(List.of(new SwingExplanation(SWING, List.of(fights))), List.of());
  }

  @Test
  void aChangedCodeOrEvidenceUnderTheSameSentenceIsInTheDiff() {
    LostFight before = lost(5, 0, "DIRE", 1200L, "NO_TP");
    LostFight after = lost(5, 0, "DIRE", 1200L, "NO_TP");
    after.reasons().get(0).reason().setEvidence("another note");

    List<String> changes =
        ReviewLoopDiff.changes(
            FightLoopReport.render(data(single(before))),
            FightLoopReport.render(data(single(after))),
            FightLoopTopic.SECTIONS);

    assertThat(changes)
        .containsExactly(
            "Dire lost fight 5 at 23:03 Windrunner had no teleport, evidence: no | teleport at"
                + " start -> another note");
  }

  @Test
  void twoReasonsWithTheSameSentenceKeepSeparateRows() {
    LostFight base = lost(5, 0, "DIRE", 1200L, "NO_TP");
    FightReasonDomain twin =
        FightReasonDomain.builder()
            .code("NO_TP")
            .scope("PLAYER")
            .subjectSlot(128L)
            .team("DIRE")
            .severity(0.5)
            .evidence("second")
            .build();
    LostFight fight =
        new LostFight(
            base.fight(),
            base.explanation(),
            List.of(base.reasons().get(0), new RankedReason(2, twin, List.of(), true)),
            2);

    String report = FightLoopReport.render(data(single(fight)));

    assertThat(report)
        .contains("| Windrunner had no teleport (2) | Dire lost fight 5 at 23:03 | 2 |")
        .contains("| Windrunner had no teleport (2) | NO_TP | second |");
    assertThat(
            ReviewLoopDiff.changes(report, report.replace("0.50", "0.60"), FightLoopTopic.SECTIONS))
        .containsExactly(
            "Windrunner had no teleport (2) Dire lost fight 5 at 23:03, severity: 0.50 -> 0.60");
  }

  @Test
  void unknownUnmatchedDeathsShowAMarkerAndKnownOnesTheirCount() {
    LostFight unknown = lost(5, 0, "DIRE", 1200L, "NO_TP");
    LostFight none = lost(6, 0, "DIRE", 1200L, "NO_TP");
    none.explanation().setUnmatchedDeaths(0);
    LostFight some = lost(7, 0, "DIRE", 1200L, "NO_TP");
    some.explanation().setUnmatchedDeaths(2);

    String report =
        FightLoopReport.render(
            data(
                new MatchExplanation(
                    List.of(new SwingExplanation(SWING, List.of(unknown, none, some))),
                    List.of())));

    assertThat(report)
        .contains("| 1.2k (deaths not priced: ?) | swing 0 |")
        .contains("| 1.2k | swing 0 |")
        .contains("| 1.2k (2 deaths not priced) | swing 0 |");
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
            null,
            List.of(),
            List.of());

    assertThat(FightLoopReport.render(noOd))
        .contains(
            "| Dire lost fight 5 at 23:03 | unknown | ? | ? | ? | ? | Windrunner had no teleport |"
                + " ? | none | none |");
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

    assertThat(changes)
        .containsExactly(
            "Dire lost fight 5 at 23:03, gold lost: 1.2k (deaths not priced: ?) -> 1.5k (deaths not"
                + " priced: ?)");
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
            null,
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
  void aTeamfightOverlappingTwoFightsIsShownOnlyForTheOneWithTheLargerOverlap() {
    LostFight near = lost(5, null, "DIRE", null, "NO_TP");
    LostFight far = lost(6, null, "DIRE", null, "NO_BKB");
    far.fight().setStartSeconds(1420.0);
    far.fight().setEndSeconds(1430.0);
    MatchData d = data(new MatchExplanation(List.of(), List.of(near, far)));
    d =
        new MatchData(
            d.matchId(),
            d.match(),
            d.modeName(),
            d.explanation(),
            d.heroNames(),
            null,
            List.of(
                TeamfightDomain.builder()
                    .fightIndex(1)
                    .startTime(1395L)
                    .endTime(1415L)
                    .deaths(4L)
                    .build()),
            List.of());

    List<String> rows =
        FightLoopReport.render(d)
            .lines()
            .filter(l -> l.startsWith("| Dire lost fight") && l.contains("| unknown |"))
            .toList();

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).endsWith("| 4 deaths, ? gold change for the loser |");
    assertThat(rows.get(1)).endsWith("| none |");
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

  private static LostFight important(LostFight base, boolean important, long gold, long xp) {
    return new LostFight(
        base.fight(),
        base.explanation(),
        base.reasons(),
        base.totalReasons(),
        FightImportanceDomain.builder()
            .fightIndex(base.fight().getFightIndex())
            .winningTeam("RADIANT")
            .goldChange(gold)
            .xpChange(xp)
            .sharePercent(4.25)
            .leadFlip(true)
            .flipToWinner(true)
            .slopeBreak(false)
            .important(important)
            .build());
  }

  private static MatchData withSeries(MatchExplanation explanation) {
    MatchData d = data(explanation);
    double[] gold = {0, 500, -300, 2300, 2300};
    double[] xp = {0, 100, 200, 900, 900};
    double[] nw = {20000, 20000, 20000, 20000, 20000};
    return new MatchData(
        d.matchId(),
        d.match(),
        d.modeName(),
        d.explanation(),
        d.heroNames(),
        new AdvantageSeries(gold, xp, nw, nw),
        d.openDotaFights(),
        d.openDotaPlayers());
  }

  @Test
  void anUnimportantFightIsListedWithItsNumbersButHasNoReasonsAndTheSummaryCountsBoth() {
    LostFight big = important(lost(5, 0, "DIRE", 1200L, "NO_TP"), true, 2600, 700);
    LostFight small = important(lost(6, 0, "DIRE", 300L, "NO_BKB"), false, 150, 20);

    String report = FightLoopReport.render(withSeries(single(big, small)));

    assertThat(report)
        .contains("**Summary:** 1 of 2 decided fights are important (Dire lost 1)")
        .contains(
            "| Dire lost fight 5 at 23:03 | yes | Radiant +2.6k | Radiant +700 | 4.3% | lead"
                + " flipped to the winner | Windrunner had no teleport |")
        .contains(
            "| Dire lost fight 6 at 23:03 | no | Radiant +150 | Radiant +20 | 4.3% | lead flipped"
                + " to the winner | not told (unimportant) |")
        .contains("| Windrunner had no teleport | Dire lost fight 5 at 23:03 | 1 |")
        .doesNotContain("| Dire lost fight 6 at 23:03 | 1 |")
        .doesNotContain("NO_BKB");
  }

  @Test
  void theAdvantageGraphComesFirstWithSignedReadableLeadsAndMarks() {
    LostFight big = important(lost(5, 0, "DIRE", 1200L, "NO_TP"), true, 2600, 700);
    big.fight().setStartSeconds(190.0);

    String report = FightLoopReport.render(withSeries(single(big)));

    assertThat(report.indexOf("## " + FightLoopReport.GRAPH))
        .isLessThan(report.indexOf("## " + FightLoopReport.FIGHTS))
        .isPositive();
    assertThat(report)
        .contains("| minute | gold advantage | XP advantage | what happened |")
        .contains("| 1:00 | Radiant +500 | Radiant +100 |")
        .contains("| 2:00 | Dire +300 | Radiant +200 | gold lead goes to Dire |")
        .contains(
            "| 3:00 | Radiant +2.3k | Radiant +900 | gold lead goes to Radiant; important fight 5"
                + " at 3:10: Dire lost, Radiant +2.6k gold |");
  }

  @Test
  void aMatchWithoutASeriesSaysSoAndTreatsEveryFightAsImportant() {
    String report = FightLoopReport.render(data(single(lost(5, 0, "DIRE", 1200L, "NO_TP"))));

    assertThat(report)
        .contains("No advantage series stored")
        .contains("**Summary:** 1 decided fight, importance not known")
        .contains("| Windrunner had no teleport | Dire lost fight 5 at 23:03 | 1 |");
  }
}
