package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({FightExplanationReader.class, FightLossProperties.class})
class FightExplanationReaderTest {
  private static final long MATCH = 71L;

  @Autowired private FightExplanationReader reader;
  @Autowired private FightRepo fightRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightImportanceRepo importanceRepo;

  private void swing(int index, String benefiting) {
    spikeRepo.save(
        GameSpikeDomain.builder()
            .matchId(MATCH)
            .spikeIndex(index)
            .benefitingTeam(benefiting)
            .build());
  }

  private void lost(int fight, Integer swing, String loser, Long gold, int reasons) {
    fightRepo.save(FightDomain.builder().matchId(MATCH).fightIndex(fight).build());
    explanationRepo.save(
        FightExplanationDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .spikeIndex(swing)
            .losingTeam(loser)
            .goldLost(gold)
            .reasonCount(reasons)
            .build());
  }

  private void reason(int fight, String team, Long slot, String code, double severity) {
    reasonRepo.save(
        FightReasonDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .team(team)
            .scope(slot == null ? "TEAM" : "PLAYER")
            .subjectSlot(slot)
            .code(code)
            .severity(severity)
            .evidence("e")
            .build());
  }

  private void stored() {
    swing(0, "RADIANT");
    lost(0, 0, "DIRE", 300L, 4);
    lost(1, 0, "DIRE", 900L, 0);
    lost(2, 0, "DIRE", null, 0);
    lost(3, 0, "RADIANT", 5000L, 0);
    lost(4, null, "DIRE", 100L, 0);
    reason(0, "DIRE", 128L, "NO_TP", 0.7);
    reason(0, "DIRE", 129L, "TOWER", 0.4);
    reason(0, "DIRE", null, "NO_DISABLE", 0.9);
    reason(0, "DIRE", 130L, "NO_BKB", 0.3);
    reason(0, "RADIANT", 1L, "DIED_FIRST", 0.9);
  }

  @Test
  void aSwingListsItsLosersFightsCostliestFirstUpToTheLimit_theRestAreOther() {
    stored();

    MatchExplanation read = reader.read(MATCH, 3, 2);

    assertThat(read.swings()).hasSize(1);
    assertThat(read.swings().get(0).fights())
        .extracting(f -> f.fight().getFightIndex())
        .containsExactly(1, 0);
    assertThat(read.otherFights()).extracting(f -> f.fight().getFightIndex()).containsExactly(3, 4);
  }

  @Test
  void reasonsOfTheLosingTeamAreRankedAndCutToTopReasons_withTheTotalKept() {
    stored();

    var fight = reader.read(MATCH, 3, 5).swings().get(0).fights().get(1);

    assertThat(fight.reasons())
        .extracting(r -> r.rank() + " " + r.reason().getCode())
        .containsExactly("1 NO_TP", "2 TOWER", "3 NO_BKB");
    assertThat(fight.totalReasons()).isEqualTo(4);
  }

  @Test
  void anEarlyFightKeepsItsCooldownReasonsUnrankedAfterTheRankedOnes_andCollapsesAbsence() {
    fightRepo.save(FightDomain.builder().matchId(MATCH).fightIndex(7).startSeconds(120.0).build());
    explanationRepo.save(
        FightExplanationDomain.builder()
            .matchId(MATCH)
            .fightIndex(7)
            .losingTeam("DIRE")
            .reasonCount(4)
            .build());
    reason(7, "DIRE", 128L, "ABILITY_ON_COOLDOWN", 0.9);
    reason(7, "DIRE", 129L, "NO_TP", 0.4);
    reason(7, "DIRE", 129L, "SUPPORT_ABSENT", 0.6);
    reason(7, "DIRE", 130L, "INTENT_NOT_PLAYED", 0.8);

    var fight = reader.read(MATCH, 5, 5).otherFights().get(0);

    assertThat(fight.reasons())
        .extracting(r -> r.rank() + " " + r.reason().getCode() + " " + r.also() + " " + r.ranked())
        .containsExactly(
            "1 SUPPORT_ABSENT [NO_TP] true",
            "2 INTENT_NOT_PLAYED [] true",
            "0 ABILITY_ON_COOLDOWN [] false");
    assertThat(fight.totalReasons()).isEqualTo(4);
  }

  @Test
  void nothingIsCappedWhenTheLimitsAreUnbounded() {
    stored();

    var fight = reader.read(MATCH, Integer.MAX_VALUE, Integer.MAX_VALUE).swings().get(0);

    assertThat(fight.fights()).hasSize(3);
    assertThat(fight.fights().get(1).reasons())
        .extracting(r -> r.reason().getCode())
        .containsExactly("NO_TP", "TOWER", "NO_BKB", "NO_DISABLE");
  }

  @Test
  void aMatchWithNothingStoredReadsAsEmpty() {
    MatchExplanation read = reader.read(MATCH, 3, 2);

    assertThat(read.swings()).isEmpty();
    assertThat(read.otherFights()).isEmpty();
  }

  private void importance(int fight, boolean important) {
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .winningTeam("RADIANT")
            .important(important)
            .build());
  }

  @Test
  void readTellsOnlyTheImportantFights_readAllKeepsTheOthersWithTheirImportance() {
    stored();
    importance(0, true);
    importance(1, false);
    importance(2, false);
    importance(3, true);
    importance(4, false);

    MatchExplanation important = reader.read(MATCH, 3, 5);
    MatchExplanation all = reader.readAll(MATCH, 3, 5);

    assertThat(important.swings().get(0).fights())
        .extracting(f -> f.fight().getFightIndex())
        .containsExactly(0);
    assertThat(important.otherFights())
        .extracting(f -> f.fight().getFightIndex())
        .containsExactly(3);
    assertThat(all.swings().get(0).fights()).hasSize(3);
    assertThat(all.swings().get(0).fights())
        .extracting(f -> f.important())
        .containsExactlyInAnyOrder(true, false, false);
    assertThat(all.otherFights())
        .extracting(f -> f.importance().isImportant())
        .containsExactly(true, false);
  }

  @Test
  void aMatchWithNoImportanceStoredReadsEveryLostFight() {
    stored();

    assertThat(reader.read(MATCH, 3, 5).swings().get(0).fights()).hasSize(3);
    assertThat(reader.read(MATCH, 3, 5).otherFights()).hasSize(2);
  }

  @Test
  void onceAMatchHasImportanceAFightWithoutARowIsNotImportant_andRowsOfGoneFightsAreIgnored() {
    stored();
    importance(0, true);
    importance(99, true);

    MatchExplanation important = reader.read(MATCH, 3, 5);
    MatchExplanation all = reader.readAll(MATCH, 3, 5);

    assertThat(important.swings().get(0).fights())
        .extracting(f -> f.fight().getFightIndex())
        .containsExactly(0);
    assertThat(important.otherFights()).isEmpty();
    assertThat(all.otherFights()).extracting(f -> f.important()).containsOnly(false);
  }

  @Test
  void aRowOnlyOfAGoneFightLeavesTheMatchWithoutImportance_soEveryFightReads() {
    stored();
    importance(99, false);

    assertThat(reader.read(MATCH, 3, 5).swings().get(0).fights()).hasSize(3);
  }

  @Test
  void aSwingWithNoImportantLostFightKeepsItsCostliestOne_flagged() {
    stored();
    importance(0, false);
    importance(1, false);
    importance(2, false);
    importance(3, true);
    importance(4, false);

    var swing = reader.read(MATCH, 3, 5).swings().get(0);

    assertThat(swing.noImportantFight()).isTrue();
    assertThat(swing.fights()).extracting(f -> f.fight().getFightIndex()).containsExactly(1);
    assertThat(reader.readAll(MATCH, 3, 5).swings().get(0).noImportantFight()).isFalse();
  }
}
