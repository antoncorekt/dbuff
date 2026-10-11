package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  ActivityAnalyzer.class,
  CombatLogReader.class,
  MapLayouts.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  ActivityAnalyzerTest.Json.class
})
class ActivityAnalyzerTest {

  private static final long MATCH = 7L;

  @Autowired private ActivityAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private PlayerActivitySegmentRepo segmentRepo;

  @Test
  void analyze_storesTheHerosSegmentsAndReplacesAnEarlierRun() {
    stored(layouts.frozenVersion());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<PlayerActivitySegmentDomain> rows = segmentRepo.findAll();
    assertThat(rows)
        .extracting(PlayerActivitySegmentDomain::getActivity)
        .containsExactly("IN_BASE", "LANE_FARM");
    assertThat(rows).allMatch(r -> r.getPlayerSlot() == 0L && r.getAnalysisVersion() == 1);
    assertThat(rows.getFirst().getStartSeconds()).isEqualTo(-90);
  }

  @Test
  void analyze_storesNothingForAMatchPlayedOnAnotherMapVersion() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(segmentRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheSegments() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(segmentRepo.findAll()).isEmpty();
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
    playerRepo.saveAndFlush(
        ReplayPlayerDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .replayPlayerId(0)
            .teamNum(2)
            .heroEntityName("Sniper")
            .build());
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .startSeconds(-90)
            .stepSeconds(5)
            .sampleCount(2)
            .x(new int[] {-5920, 4859})
            .y(new int[] {-5352, -6379})
            .hpPercent(new int[] {100, 100})
            .manaPercent(new int[] {100, 100})
            .level(new int[] {1, 1})
            .flags(new int[] {1, 1})
            .build());
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.DEATH,
                -75,
                "npc_dota_hero_sniper",
                "npc_dota_creep_goodguys_melee",
                "",
                CombatEvent.ATTACKER_HERO,
                0,
                0,
                0,
                0,
                0,
                0,
                0));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
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
