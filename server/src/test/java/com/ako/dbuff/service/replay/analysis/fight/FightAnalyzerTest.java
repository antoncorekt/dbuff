package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
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
import java.util.ArrayList;
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
  FightAnalyzer.class,
  CombatLogReader.class,
  MapLayouts.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  FightAnalyzerTest.Json.class
})
class FightAnalyzerTest {

  private static final long MATCH = 9L;

  @Autowired private FightAnalyzer analyzer;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;

  @Test
  void analyze_storesTheFightAndAnEntryForEveryPlayer_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<FightDomain> fights = fightRepo.findAll();
    assertThat(fights).hasSize(1);
    assertThat(fights.getFirst().getType()).isEqualTo("SKIRMISH");
    assertThat(fights.getFirst().getDireDeaths()).isEqualTo(1);
    assertThat(fights.getFirst().getWinner()).isEqualTo("RADIANT");
    List<FightPlayerDomain> players = fightPlayerRepo.findAll();
    assertThat(players).hasSize(10);
    assertThat(players).extracting(FightPlayerDomain::getPlayerSlot).contains(0L, 4L, 128L, 132L);
    assertThat(players.stream().filter(p -> p.getPresence().equals("ACTIVE")).count()).isEqualTo(2);
  }

  @Test
  void analyze_copesWithAPlayerWhoseTrackHasNoSamples() {
    stored();
    ReplayPlayerTrackDomain empty = trackRepo.findAll().getFirst();
    empty.setSampleCount(0);
    empty.setX(new int[0]);
    empty.setY(new int[0]);
    empty.setHpPercent(new int[0]);
    empty.setManaPercent(new int[0]);
    empty.setLevel(new int[0]);
    empty.setFlags(new int[0]);
    trackRepo.saveAndFlush(empty);

    analyzer.analyze(MATCH);

    assertThat(fightRepo.findAll()).hasSize(1);
    assertThat(fightPlayerRepo.findAll()).hasSize(10);
  }

  @Test
  void deleteByMatchId_removesFightsAndTheirPlayers() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(fightRepo.findAll()).isEmpty();
    assertThat(fightPlayerRepo.findAll()).isEmpty();
  }

  private void stored() {
    for (int i = 0; i < 10; i++) {
      playerRepo.saveAndFlush(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(i < 5 ? (long) i : 128L + i - 5)
              .replayPlayerId(i * 2)
              .teamNum(i < 5 ? 2 : 3)
              .heroEntityName("Hero" + i)
              .build());
      trackRepo.saveAndFlush(
          ReplayPlayerTrackDomain.builder()
              .matchId(MATCH)
              .playerSlot(i < 5 ? (long) i : 128L + i - 5)
              .startSeconds(0)
              .stepSeconds(5)
              .sampleCount(40)
              .x(filled(40, 100 * i))
              .y(filled(40, 0))
              .hpPercent(filled(40, 100))
              .manaPercent(filled(40, 100))
              .level(filled(40, 10))
              .flags(filled(40, 1))
              .build());
    }
    List<CombatEvent> events = new ArrayList<>();
    for (double t : new double[] {100, 100.5, 101}) {
      events.add(
          new CombatEvent(
              CombatEventKind.DAMAGE,
              t,
              "npc_dota_hero_hero0",
              "npc_dota_hero_hero5",
              "",
              CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
              300,
              0,
              0,
              0,
              0,
              0,
              0));
    }
    events.add(
        new CombatEvent(
            CombatEventKind.DEATH,
            102,
            "npc_dota_hero_hero0",
            "npc_dota_hero_hero5",
            "",
            CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            1));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
  }

  private static int[] filled(int size, int value) {
    int[] values = new int[size];
    java.util.Arrays.fill(values, value);
    return values;
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
