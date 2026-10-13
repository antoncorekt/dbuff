package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamSplitStretchRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
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
  BehaviourSplitAnalyzer.class,
  BehaviourProperties.class,
  PlayerTraces.class,
  SlotTraces.class,
  StoredEventTimes.class,
  StoredActivity.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  BehaviourSplitAnalyzerTest.Json.class
})
class BehaviourSplitAnalyzerTest {
  private static final long MATCH = 14L;
  private static final int SAMPLES = 241;

  @Autowired private BehaviourSplitAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private LaneResultRepo laneResultRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;
  @Autowired private TeamSplitStretchRepo splitRepo;

  @Test
  void startSeconds_isTheEndOfTheLatestLanePairElseTheFallback() {
    BehaviourProperties properties = new BehaviourProperties();

    assertThat(
            BehaviourSplitAnalyzer.startSeconds(
                List.of(
                    LaneResultDomain.builder().endSeconds(540).build(),
                    LaneResultDomain.builder().endSeconds(610).build(),
                    LaneResultDomain.builder().build()),
                properties))
        .isEqualTo(610);
    assertThat(BehaviourSplitAnalyzer.startSeconds(List.of(), properties)).isEqualTo(720);
  }

  @Test
  void analyze_storesTheStretchesOfBothTeamsWithTheirTowersAndReplacesThemPerMatch() {
    stored(layouts.frozenVersion());
    buildingRepo.saveAndFlush(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .kind("TOWER")
            .ownerTeam("DIRE")
            .seconds(800.0)
            .build());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<TeamSplitStretchDomain> rows = splitRepo.findAll();
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.getLabel()).isEqualTo("GROUPED");
              assertThat(r.getStartSeconds()).isEqualTo(720);
              assertThat(r.getEndSeconds()).isEqualTo(1200);
              assertThat(r.getAnalysisVersion()).isEqualTo(1);
            });
    TeamSplitStretchDomain radiant =
        rows.stream().filter(r -> "RADIANT".equals(r.getTeam())).findFirst().orElseThrow();
    TeamSplitStretchDomain dire =
        rows.stream().filter(r -> "DIRE".equals(r.getTeam())).findFirst().orElseThrow();
    assertThat(radiant.getTowersTaken()).isEqualTo(1);
    assertThat(dire.getTowersLost()).isEqualTo(1);
  }

  @Test
  void analyze_storesNothingForAMatchPlayedOnAnotherMapVersion() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(splitRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheStretches() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(splitRepo.findAll()).isEmpty();
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
    int[] where = new int[SAMPLES];
    Arrays.fill(where, -1000);
    int[] ones = new int[SAMPLES];
    Arrays.fill(ones, 1);
    for (long slot : new long[] {0, 1, 2, 3, 4, 128, 129, 130, 131, 132}) {
      trackRepo.saveAndFlush(
          ReplayPlayerTrackDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .startSeconds(0)
              .stepSeconds(5)
              .sampleCount(SAMPLES)
              .x(where)
              .y(where)
              .hpPercent(ones)
              .manaPercent(ones)
              .level(ones)
              .flags(ones)
              .build());
    }
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
