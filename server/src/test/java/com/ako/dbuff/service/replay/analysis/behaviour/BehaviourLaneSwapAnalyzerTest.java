package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamLaneSwapRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  BehaviourLaneSwapAnalyzer.class,
  BehaviourProperties.class,
  PlayerTraces.class,
  SlotTraces.class,
  StoredLaneSlots.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  BehaviourLaneSwapAnalyzerTest.Json.class
})
class BehaviourLaneSwapAnalyzerTest {
  private static final long MATCH = 15L;
  private static final int SAMPLES = 150;

  @Autowired private BehaviourLaneSwapAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private MatchMapLayout matchMapLayout;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private LaneAssignmentRepo laneRepo;
  @Autowired private TeamLaneSwapRepo swapRepo;

  @Test
  void analyze_storesTheHeroWhoStoodInAnotherLaneThanHisStoredOneAndReplacesPerMatch() {
    stored(layouts.frozenVersion());
    var lane = matchMapLayout.of(MATCH).orElseThrow().zone(-6000, 5500).lane();
    assertThat(lane).isNotNull();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<TeamLaneSwapDomain> rows = swapRepo.findAll();
    assertThat(rows).hasSize(1);
    TeamLaneSwapDomain row = rows.getFirst();
    assertThat(row.getPlayerSlot()).isEqualTo(128L);
    assertThat(row.getTeam()).isEqualTo("DIRE");
    assertThat(row.getLaneAfter()).isEqualTo(lane.name());
    assertThat(row.getLaneBefore()).isNotEqualTo(lane.name());
    assertThat(row.getFromSeconds()).isEqualTo(480);
    assertThat(row.getAnalysisVersion()).isEqualTo(1);
  }

  @Test
  void analyze_storesNothingForAMatchPlayedOnAnotherMapVersion() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(swapRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheSwaps() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(swapRepo.findAll()).isEmpty();
  }

  private void stored(String mapVersion) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(processing.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .mapVersion(mapVersion)
            .build());
    String other =
        matchMapLayout
            .of(MATCH)
            .map(layout -> "TOP".equals(layout.zone(-6000, 5500).lane().name()) ? "MID" : "TOP")
            .orElse("TOP");
    track(128L, -6000, 5500);
    track(0L, -1000, -1000);
    laneRepo.saveAndFlush(assignment(128L, "DIRE", other));
    laneRepo.saveAndFlush(assignment(0L, "RADIANT", "MID"));
  }

  private static LaneAssignmentDomain assignment(long slot, String team, String lane) {
    return LaneAssignmentDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .team(team)
        .lane(lane)
        .startSeconds(0)
        .endSeconds(400)
        .build();
  }

  private void track(long slot, int x, int y) {
    int[] xs = new int[SAMPLES];
    int[] ys = new int[SAMPLES];
    int[] ones = new int[SAMPLES];
    Arrays.fill(xs, x);
    Arrays.fill(ys, y);
    Arrays.fill(ones, 1);
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(SAMPLES)
            .x(xs)
            .y(ys)
            .hpPercent(ones)
            .manaPercent(ones)
            .level(ones)
            .flags(ones)
            .build());
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
