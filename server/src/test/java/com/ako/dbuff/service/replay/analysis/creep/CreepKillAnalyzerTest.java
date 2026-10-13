package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogBlobs;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
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
  CreepKillAnalyzer.class,
  CombatLogReader.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  CreepKillAnalyzerTest.Json.class
})
class CreepKillAnalyzerTest {

  private static final long MATCH = 9L;
  private static final int BOTTOM_LANE_X = 4859;
  private static final int BOTTOM_LANE_Y = -6379;

  @Autowired private CreepKillAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private CreepKillRepo repo;

  @Test
  void analyze_storesEachKillWithItsClassSideGoldAndZone_andReplacesAnEarlierRun() {
    stored(layouts.frozenVersion());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<CreepKillDomain> rows = repo.findByMatchId(MATCH);
    assertThat(rows).hasSize(3);
    CreepKillDomain melee = row(rows, "LANE_MELEE");
    assertThat(melee.getPlayerSlot()).isZero();
    assertThat(melee.getSide()).isEqualTo("ENEMY");
    assertThat(melee.getDenied()).isFalse();
    assertThat(melee.getGold()).isEqualTo(38);
    assertThat(melee.getXp()).isEqualTo(57);
    assertThat(melee.getZone()).isEqualTo("LANE");
    assertThat(melee.getLane()).isEqualTo("BOTTOM");
    assertThat(melee.getAnalysisVersion()).isEqualTo(1);
    CreepKillDomain siege = row(rows, "LANE_SIEGE");
    assertThat(siege.getDenied()).isTrue();
    assertThat(siege.getGold()).isNull();
    assertThat(siege.getZone()).as("a deny has no position").isNull();
    assertThat(row(rows, "NEUTRAL").getSide()).isNull();
  }

  @Test
  void analyze_storesTheKillsWithoutAZoneForAMatchOnAnotherMapVersion() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(repo.findByMatchId(MATCH)).hasSize(3).allMatch(r -> r.getZone() == null);
    assertThat(row(repo.findByMatchId(MATCH), "LANE_MELEE").getGold()).isEqualTo(38);
  }

  @Test
  void analyze_skipsAMatchWhoseCombatLogWasStoredWithoutReasons_andKeepsItsOldRows() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(1)
            .eventCount(0)
            .data(CombatLogBlobs.versionOne())
            .build());

    analyzer.analyze(MATCH);

    assertThat(repo.findByMatchId(MATCH)).hasSize(3);
  }

  @Test
  void analyze_storesNothingWithoutACombatLog() {
    analyzer.analyze(MATCH);

    assertThat(repo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheKills() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(repo.findByMatchId(MATCH)).isEmpty();
  }

  private static CreepKillDomain row(List<CreepKillDomain> rows, String creepClass) {
    return rows.stream()
        .filter(r -> r.getCreepClass().equals(creepClass))
        .findFirst()
        .orElseThrow();
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
    List<CombatEvent> events =
        List.of(
            event(CombatEventKind.GOLD, 100, "", "npc_dota_hero_sniper", 38, 13, 0),
            event(CombatEventKind.XP, 100, "", "npc_dota_hero_sniper", 57, 2, 0),
            event(
                CombatEventKind.DEATH,
                100,
                "npc_dota_hero_sniper",
                "npc_dota_creep_badguys_melee",
                0,
                0,
                CombatEvent.ATTACKER_HERO),
            event(
                CombatEventKind.DEATH,
                110,
                "npc_dota_hero_sniper",
                "npc_dota_goodguys_siege",
                0,
                0,
                CombatEvent.ATTACKER_HERO),
            event(CombatEventKind.GOLD, 120, "", "npc_dota_hero_sniper", 20, 14, 0),
            event(
                CombatEventKind.DEATH,
                120,
                "npc_dota_hero_sniper",
                "npc_dota_neutral_kobold",
                0,
                0,
                CombatEvent.ATTACKER_HERO));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
  }

  private static CombatEvent event(
      CombatEventKind kind,
      double second,
      String attacker,
      String target,
      int value,
      int aux,
      int flags) {
    return new CombatEvent(
        kind,
        second,
        attacker,
        target,
        "",
        flags | (target.startsWith("npc_dota_hero") ? CombatEvent.TARGET_HERO : 0),
        value,
        0,
        aux,
        0,
        kind == CombatEventKind.DEATH ? 0 : BOTTOM_LANE_X,
        kind == CombatEventKind.DEATH ? 0 : BOTTOM_LANE_Y,
        0);
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
