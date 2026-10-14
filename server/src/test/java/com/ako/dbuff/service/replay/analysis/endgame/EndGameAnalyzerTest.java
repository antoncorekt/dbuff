package com.ako.dbuff.service.replay.analysis.endgame;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({EndGameAnalyzer.class, EndGameProperties.class})
class EndGameAnalyzerTest {
  private static final long MATCH = 77L;
  private static final int MINUTES = 41;

  @Autowired private EndGameAnalyzer analyzer;
  @Autowired private MatchRepo matchRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private ObjectiveConversionRepo conversionRepo;
  @Autowired private BuybackEventRepo buybackRepo;
  @Autowired private EndGameSummaryRepo summaryRepo;

  /** Each player holds a fifth of the team's share of 200k; the lead is Radiant's, as a share. */
  private void match(boolean radiantWin, IntToDoubleFunction radiantLead) {
    matchRepo.save(MatchDomain.builder().id(MATCH).radiantWin(radiantWin).duration(2460L).build());
    for (int minute = 0; minute < MINUTES; minute++) {
      double s = radiantLead.applyAsDouble(minute);
      for (int p = 0; p < 10; p++) {
        boolean radiant = p < 5;
        double team = 100000 * (radiant ? 1 + s : 1 - s);
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(MATCH)
                .playerSlot(radiant ? p : 128L + p - 5)
                .gameMinute(minute)
                .networth(Math.round(team / 5))
                .build());
      }
    }
  }

  private void fight(int index, double start, String winner, long gold, boolean important) {
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .startSeconds(start)
            .endSeconds(start + 20)
            .winner(winner)
            .build());
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .winningTeam(winner)
            .goldChange(gold)
            .important(important)
            .build());
  }

  private EndGameSummaryDomain summary() {
    assertThat(summaryRepo.findByMatchId(MATCH)).hasSize(1);
    return summaryRepo.findByMatchId(MATCH).getFirst();
  }

  /** Dire leads by 15 percent from minute 15 to 24, then loses it: Radiant wins. */
  private static double dire15ThenRadiant(int minute) {
    return minute < 15 ? 0.02 : minute <= 24 ? -0.15 : minute < 30 ? 0.0 : 0.2;
  }

  @Test
  void aLoserWhoHeldALeadForLongEnoughThrewIt() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getWinnerTeam()).isEqualTo("RADIANT");
    assertThat(row.getThrown()).isTrue();
    assertThat(row.getLeadLost()).isTrue();
    assertThat(row.getPeakLeadSeconds()).isEqualTo(15 * 60);
    assertThat(row.getPeakLeadGold()).isEqualTo(30000);
    assertThat(row.getPeakLeadPercent()).isEqualTo(15.0);
    assertThat(row.getHeldSeconds()).isEqualTo(10 * 60);
    assertThat(row.getDurationSeconds()).isEqualTo(2460);
  }

  @Test
  void aLeadHeldForOneMinuteIsNotAThrow() {
    match(true, m -> m == 20 ? -0.15 : m < 30 ? 0.0 : 0.2);

    analyzer.analyze(MATCH);

    assertThat(summary().getThrown()).isFalse();
    assertThat(summary().getPeakLeadGold()).isEqualTo(30000);
  }

  @Test
  void aSmallLeadIsNotAThrow() {
    match(false, m -> m < 10 ? 0.0 : m < 30 ? 0.08 : -0.1);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getWinnerTeam()).isEqualTo("DIRE");
    assertThat(row.getThrown()).isFalse();
    assertThat(row.getLeadLost()).isTrue();
    assertThat(row.getHeldSeconds()).isZero();
  }

  @Test
  void aWinnerWhoAlwaysLedLeavesTheLoserWithoutAPeakAndATurningPoint() {
    match(true, m -> 0.1 + m * 0.005);
    fight(0, 600, "RADIANT", 3000, true);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getThrown()).isFalse();
    assertThat(row.getPeakLeadSeconds()).isNull();
    assertThat(row.getLeadLost()).isNull();
    assertThat(row.getTurningSeconds()).isNull();
    assertThat(row.getFinalFightIndex()).isZero();
  }

  @Test
  void theTurningPointIsTheFirstConvertedFightOfTheWinnerAfterThePeak() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);
    fight(0, 600, "RADIANT", 9000, true);
    fight(1, 1600, "RADIANT", 2000, true);
    fight(2, 2000, "RADIANT", 6000, true);
    fight(3, 2200, "DIRE", 500, false);
    conversionRepo.save(converted(0));
    conversionRepo.save(converted(2));

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getTurningFightIndex()).isEqualTo(2);
    assertThat(row.getTurningKind()).isEqualTo("CONVERTED");
    assertThat(row.getTurningSeconds()).isEqualTo(2000.0);
    assertThat(row.getObjectiveKey()).isEqualTo("npc_dota_badguys_tower3_top");
    assertThat(row.getObjectiveSecondsAfter()).isEqualTo(30);
    assertThat(row.getTurningGoldChange()).isEqualTo(6000);
    assertThat(row.getFinalFightIndex()).isEqualTo(2);
    assertThat(row.getFinalFightWinner()).isEqualTo("RADIANT");
  }

  @Test
  void withoutAConvertedFightTheLargestSwingAfterThePeakIsTheTurningPoint() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);
    fight(0, 600, "RADIANT", 9000, true);
    fight(1, 1600, "RADIANT", 2000, true);
    fight(2, 2000, "RADIANT", 6000, true);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getTurningKind()).isEqualTo("SWING");
    assertThat(row.getTurningFightIndex()).isEqualTo(2);
    assertThat(row.getObjectiveKey()).isNull();
  }

  @Test
  void theFinalFightIsTheLastFightWhenNoneIsImportant() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);
    fight(0, 600, "RADIANT", 100, false);
    fight(1, 1800, "DIRE", 100, false);

    analyzer.analyze(MATCH);

    assertThat(summary().getFinalFightIndex()).isEqualTo(1);
    assertThat(summary().getFinalFightWinner()).isEqualTo("DIRE");
  }

  @Test
  void buybacksAreCountedPerTeamInTheLastWindowOnly() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);
    buyback(128L, 1800.0);
    buyback(129L, 2000.0);
    buyback(130L, 2400.0);
    buyback(1L, 2300.0);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getDireBuybacks()).isEqualTo(2);
    assertThat(row.getRadiantBuybacks()).isEqualTo(1);
    assertThat(row.getBuybackWindowSeconds()).isEqualTo(600);
  }

  @Test
  void aMatchWithoutAWinnerGetsNoRow() {
    matchRepo.save(MatchDomain.builder().id(MATCH).duration(2460L).build());

    analyzer.analyze(MATCH);

    assertThat(summaryRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void withoutANetWorthTimelineTheRowStillHasTheWinnerBuybacksAndFinalFight() {
    matchRepo.save(MatchDomain.builder().id(MATCH).radiantWin(false).duration(2460L).build());
    fight(0, 600, "DIRE", 100, true);

    analyzer.analyze(MATCH);

    EndGameSummaryDomain row = summary();
    assertThat(row.getWinnerTeam()).isEqualTo("DIRE");
    assertThat(row.getThrown()).isFalse();
    assertThat(row.getPeakLeadSeconds()).isNull();
    assertThat(row.getFinalFightWinner()).isEqualTo("DIRE");
  }

  @Test
  void runningAgainReplacesTheRow() {
    match(true, EndGameAnalyzerTest::dire15ThenRadiant);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(summaryRepo.findByMatchId(MATCH)).hasSize(1);
    analyzer.deleteByMatchId(MATCH);
    assertThat(summaryRepo.findByMatchId(MATCH)).isEmpty();
  }

  private ObjectiveConversionDomain converted(int fightIndex) {
    return ObjectiveConversionDomain.builder()
        .matchId(MATCH)
        .fightIndex(fightIndex)
        .winnerTeam("RADIANT")
        .result("CONVERTED")
        .objectiveKind("TOWER")
        .objectiveKey("npc_dota_badguys_tower3_top")
        .secondsAfter(30)
        .build();
  }

  private void buyback(long slot, double seconds) {
    buybackRepo.save(
        BuybackEventDomain.builder().matchId(MATCH).playerSlot(slot).seconds(seconds).build());
  }
}
