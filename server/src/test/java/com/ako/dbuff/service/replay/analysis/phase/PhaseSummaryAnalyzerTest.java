package com.ako.dbuff.service.replay.analysis.phase;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
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
@Import({PhaseSummaryAnalyzer.class, ReplayAnalysisProperties.class})
class PhaseSummaryAnalyzerTest {

  private static final long MATCH = 16L;

  @Autowired private PhaseSummaryAnalyzer analyzer;
  @Autowired private PlayerActivitySegmentRepo segmentRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private PlayerPhaseSummaryRepo summaryRepo;

  @Test
  void analyze_sumsUpEachPhaseOfEachPlayer_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<PlayerPhaseSummaryDomain> rows = summaryRepo.findByMatchId(MATCH);
    assertThat(rows).extracting("phase").containsExactly("LANE", "MID");
    PlayerPhaseSummaryDomain lane = rows.getFirst();
    assertThat(lane.getLaneFarmSeconds()).isEqualTo(600);
    assertThat(lane.getDeadSeconds()).isEqualTo(60);
    assertThat(lane.getInBaseSeconds()).isEqualTo(60);
    assertThat(lane.getUnknownSeconds()).isZero();
    assertThat(lane.getKills()).isEqualTo(2);
    assertThat(lane.getDeaths()).isEqualTo(1);
    assertThat(lane.getGoldPerMinute()).isEqualTo(400.0);
    assertThat(lane.getFightsAvailable())
        .as("the fight he was dead for is not counted")
        .isEqualTo(1);
    assertThat(lane.getFightsJoined()).isEqualTo(1);
    PlayerPhaseSummaryDomain mid = rows.get(1);
    assertThat(mid.getJungleFarmSeconds()).isEqualTo(280);
    assertThat(mid.getKills()).isEqualTo(1);
  }

  @Test
  void deleteByMatchId_removesTheSummaries() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(summaryRepo.findAll()).isEmpty();
  }

  private void stored() {
    segment(0, 600, "LANE_FARM");
    segment(600, 660, "DEAD");
    segment(660, 720, "IN_BASE");
    segment(720, 1000, "JUNGLE_FARM");
    for (int minute = 0; minute <= 16; minute++) {
      minuteRepo.save(
          ReplayPlayerMinuteDomain.builder()
              .matchId(MATCH)
              .playerSlot(0L)
              .gameMinute(minute)
              .kills(minute < 5 ? 0L : minute < 13 ? 2L : 3L)
              .deaths(minute < 10 ? 0L : 1L)
              .assists(0L)
              .build());
      timelineRepo.save(
          PlayerTimelineDomain.builder()
              .matchId(MATCH)
              .playerSlot(0L)
              .gameMinute(minute)
              .gold(minute * 400L)
              .xp(minute * 500L)
              .build());
    }
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .type("SKIRMISH")
            .startSeconds(300.0)
            .endSeconds(320.0)
            .build());
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .type("SKIRMISH")
            .startSeconds(610.0)
            .endSeconds(630.0)
            .build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(0L)
            .presence("ACTIVE")
            .build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .playerSlot(0L)
            .presence("ABSENT_DEAD")
            .build());
    segmentRepo.flush();
  }

  private void segment(int start, int end, String activity) {
    segmentRepo.save(
        PlayerActivitySegmentDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .startSeconds(start)
            .endSeconds(end)
            .activity(activity)
            .zone("LANE")
            .build());
  }
}
