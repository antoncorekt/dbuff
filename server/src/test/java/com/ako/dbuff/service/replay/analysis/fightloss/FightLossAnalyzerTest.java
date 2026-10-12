package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.reason.FightReasonAnalyzer;
import com.ako.dbuff.service.replay.analysis.spike.SpikeAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({FightLossAnalyzer.class, FightLossProperties.class})
class FightLossAnalyzerTest {
  private static final long MATCH = 61L;

  @Autowired private FightLossAnalyzer analyzer;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private GameSpikeFactorRepo factorRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private DeathEventRepo deathEventRepo;

  private void fight(int index, String winner) {
    fightRepo.save(FightDomain.builder().matchId(MATCH).fightIndex(index).winner(winner).build());
  }

  private void reason(int fight, String team, Long slot, String code) {
    reasonRepo.save(
        FightReasonDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .team(team)
            .scope(slot == null ? "TEAM" : "PLAYER")
            .subjectSlot(slot)
            .code(code)
            .severity(0.5)
            .build());
  }

  private void swing(int index, String benefiting, int... fights) {
    spikeRepo.save(
        GameSpikeDomain.builder()
            .matchId(MATCH)
            .spikeIndex(index)
            .benefitingTeam(benefiting)
            .build());
    for (int fight : fights) {
      factorRepo.save(
          GameSpikeFactorDomain.builder()
              .matchId(MATCH)
              .spikeIndex(index)
              .factorType("FIGHT")
              .ref(String.valueOf(fight))
              .build());
    }
  }

  private void death(long slot, int fight, double seconds, Long goldLost) {
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .deathIndex((int) seconds)
            .deathSeconds(seconds)
            .fightIndex(fight)
            .build());
    if (goldLost != null) {
      deathEventRepo.save(
          DeathEventDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .deathIndex((int) seconds)
              .deathTime(Math.round(seconds) + 1)
              .goldLost(goldLost)
              .build());
    }
  }

  @Test
  void everyFightWithAWinnerGetsTheLoserSideReasonCountSwingAndGold() {
    fight(0, "RADIANT");
    fight(1, "DIRE");
    fight(2, "DRAW");
    fight(3, null);
    reason(0, "DIRE", 128L, "NO_TP");
    reason(0, "DIRE", 129L, "TOWER");
    reason(0, "RADIANT", 1L, "DIED_FIRST");
    swing(0, "RADIANT", 0);
    death(128, 0, 100, 300L);
    death(129, 0, 101, 200L);
    death(130, 0, 102, null);
    death(1, 0, 103, 999L);

    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(
            FightExplanationDomain::getFightIndex,
            FightExplanationDomain::getLosingTeam,
            FightExplanationDomain::getSpikeIndex,
            FightExplanationDomain::getGoldLost,
            FightExplanationDomain::getUnmatchedDeaths,
            FightExplanationDomain::getReasonCount,
            FightExplanationDomain::getAnalysisVersion)
        .containsExactlyInAnyOrder(
            tuple(0, "DIRE", 0, 500L, 1, 2, 1), tuple(1, "RADIANT", null, null, 0, 0, 1));
  }

  @Test
  void aFightListedByTwoSwingsBelongsToTheOneItsWinnerBenefitedFrom_andARunReplacesTheLast() {
    fight(0, "DIRE");
    swing(0, "RADIANT", 0);
    swing(1, "DIRE", 0);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(FightExplanationDomain::getSpikeIndex)
        .containsExactly(1);
  }

  @Test
  void aDeathWithNoOpenDotaFigureNearItCostsNothingKnown() {
    fight(0, "RADIANT");
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .deathIndex(0)
            .deathSeconds(100.0)
            .fightIndex(0)
            .build());
    deathEventRepo.save(
        DeathEventDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .deathIndex(0)
            .deathTime(160L)
            .goldLost(400L)
            .build());

    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(FightExplanationDomain::getGoldLost)
        .containsExactly((Long) null);
  }

  @Test
  void anOpenDotaDeathPaysForOneReplayDeathOnly_andTheOtherCountsAsUnmatched() {
    fight(0, "RADIANT");
    death(128, 0, 100, 400L);
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .deathIndex(1)
            .deathSeconds(102.5)
            .fightIndex(0)
            .build());

    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(FightExplanationDomain::getGoldLost, FightExplanationDomain::getUnmatchedDeaths)
        .containsExactly(tuple(400L, 1));
  }

  @Test
  void runsAfterTheReasonAndSpikeAnalyzers() {
    int loss = AnnotationUtils.findAnnotation(FightLossAnalyzer.class, Order.class).value();

    assertThat(loss)
        .isGreaterThan(
            AnnotationUtils.findAnnotation(FightReasonAnalyzer.class, Order.class).value())
        .isGreaterThan(AnnotationUtils.findAnnotation(SpikeAnalyzer.class, Order.class).value());
  }

  @Test
  void aMatchWithoutFightsGetsNoRows() {
    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(List.of(analyzer.name())).containsExactly("fight-loss");
  }
}
