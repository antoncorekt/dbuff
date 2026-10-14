package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({OutlierAnalyzer.class, ReplayAnalysisProperties.class})
class OutlierAnalyzerTest {

  private static final long MATCH = 18L;

  @Autowired private OutlierAnalyzer analyzer;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private PlayerOutlierRepo outlierRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;

  /** Radiant slot 0 holds 60 percent of the team's worth from minute 2 and joined no fight. */
  private void stored() {
    for (int minute = 0; minute < 10; minute++) {
      for (int p = 0; p < 10; p++) {
        boolean radiant = p < 5;
        long slot = radiant ? p : 128L + p - 5;
        long worth = 4000;
        if (p == 0 && minute >= 2) {
          worth = 24000;
        }
        if (p == 4 && minute >= 2) {
          worth = 100;
        }
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(MATCH)
                .playerSlot(slot)
                .gameMinute(minute)
                .networth(worth)
                .build());
      }
    }
    for (int fight = 0; fight < 4; fight++) {
      for (long slot : new long[] {1, 2, 3, 4}) {
        fightPlayerRepo.save(
            FightPlayerDomain.builder()
                .matchId(MATCH)
                .fightIndex(fight)
                .playerSlot(slot)
                .presence("ACTIVE")
                .build());
      }
      fightPlayerRepo.save(
          FightPlayerDomain.builder()
              .matchId(MATCH)
              .fightIndex(fight)
              .playerSlot(0L)
              .presence("ABSENT_FAR")
              .build());
    }
    for (int i = 0; i < 6; i++) {
      deathRepo.save(
          DeathAnalysisDomain.builder()
              .matchId(MATCH)
              .playerSlot(4L)
              .deathIndex(i)
              .deathSeconds(100.0 * i)
              .reason("OTHER")
              .build());
    }
    timelineRepo.flush();
  }

  @Test
  void analyze_flagsABigShareWithNoFightsAndASmallShareWithManyDeaths_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<PlayerOutlierDomain> rows = outlierRepo.findByMatchId(MATCH);
    assertThat(rows)
        .extracting("playerSlot", "kind")
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(0L, "NW_OUTLIER"),
            org.assertj.core.groups.Tuple.tuple(4L, "FEEDER"));
    PlayerOutlierDomain big =
        rows.stream().filter(r -> r.getKind().equals("NW_OUTLIER")).findFirst().orElseThrow();
    assertThat(big.getStartSeconds()).isEqualTo(120);
    assertThat(big.getSharePercent()).isGreaterThan(50.0);
    assertThat(big.getFightsJoined()).isZero();
    assertThat(big.getParticipationRank()).isEqualTo(5);
  }

  @Test
  void analyze_findsNothingWithoutANetWorthTimeline() {
    analyzer.analyze(MATCH);

    assertThat(outlierRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheOutliers() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(outlierRepo.findAll()).isEmpty();
  }

  @Test
  void analyze_explainsAnOutlierByTheIncomeBreakAndTheMainGoldSource() {
    for (int minute = 0; minute < 13; minute++) {
      for (int p = 0; p < 10; p++) {
        long slot = p < 5 ? p : 128L + p - 5;
        long worth = 4000 + 300L * minute;
        if (p == 0 && minute > 3) {
          worth = 4900 + 1500L * (minute - 3);
        }
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(MATCH)
                .playerSlot(slot)
                .gameMinute(minute)
                .networth(worth)
                .build());
      }
      minuteRepo.save(
          ReplayPlayerMinuteDomain.builder()
              .matchId(MATCH)
              .playerSlot(0L)
              .gameMinute(minute)
              .creepKillGold(100L * minute)
              .neutralKillGold(1000L * minute)
              .build());
    }
    for (int fight = 0; fight < 4; fight++) {
      for (long slot : new long[] {1, 2, 3, 4}) {
        fightPlayerRepo.save(
            FightPlayerDomain.builder()
                .matchId(MATCH)
                .fightIndex(fight)
                .playerSlot(slot)
                .presence("ACTIVE")
                .build());
      }
    }

    analyzer.analyze(MATCH);

    PlayerOutlierDomain row = outlierRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getKind()).isEqualTo("NW_OUTLIER");
    assertThat(row.getIncomeBreakSeconds()).isEqualTo(180);
    assertThat(row.getMainIncomeSource()).isEqualTo("JUNGLE_CREEPS");
    assertThat(row.getAnalysisVersion()).isEqualTo(2);
    assertThat(row.getExplanation())
        .isEqualTo(
            "his income rose from minute 3 and came mostly from jungle creeps; he joined 0 of 4"
                + " fights, fewer than the team median of 4");
  }

  @Test
  void analyze_leavesTheExplanationPartsNullWhereTheDataIsMissing() {
    stored();

    analyzer.analyze(MATCH);

    PlayerOutlierDomain big =
        outlierRepo.findByMatchId(MATCH).stream()
            .filter(r -> r.getKind().equals("NW_OUTLIER"))
            .findFirst()
            .orElseThrow();
    assertThat(big.getIncomeBreakSeconds()).isNull();
    assertThat(big.getMainIncomeSource()).isNull();
    assertThat(big.getExplanation())
        .isEqualTo("he joined 0 of 4 fights, fewer than the team median of 4");
  }

  @Test
  void requires_namesWhatItReads() {
    assertThat(analyzer.requires()).containsExactlyInAnyOrder("fights", "deaths", "minutes");
  }
}
