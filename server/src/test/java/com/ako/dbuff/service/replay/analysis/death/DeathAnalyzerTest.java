package com.ako.dbuff.service.replay.analysis.death;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import com.ako.dbuff.service.replay.map.MapLayouts;
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
  DeathAnalyzer.class,
  CombatLogReader.class,
  HeroStateTimelines.class,
  PlayerTraces.class,
  MapLayouts.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  DeathAnalyzerTest.Json.class
})
class DeathAnalyzerTest {

  private static final long MATCH = 11L;

  @Autowired private DeathAnalyzer analyzer;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;

  @Test
  void analyze_storesEachDeathWithItsReasonOnOpenDotaSlots_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<DeathAnalysisDomain> deaths = deathRepo.findByMatchId(MATCH);
    assertThat(deaths).hasSize(1);
    DeathAnalysisDomain death = deaths.getFirst();
    assertThat(death.getPlayerSlot()).isEqualTo(128L);
    assertThat(death.getDeathIndex()).isZero();
    assertThat(death.getKillerKind()).isEqualTo("HERO");
    assertThat(death.getBkbOwned()).isTrue();
    assertThat(death.getReason()).isEqualTo("BKB_READY_UNUSED");
    assertThat(death.getReincarnated()).isFalse();
    assertThat(death.getTopSources()).startsWith("npc_dota_hero_hero0:");
  }

  @Test
  void analyze_carriesTheFrontlineDeltaOfTheFightTheHeroDiedIn() {
    stored();
    fightRepo.saveAndFlush(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .type("SKIRMISH")
            .startSeconds(95.0)
            .endSeconds(110.0)
            .build());
    fightPlayerRepo.saveAndFlush(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(128L)
            .frontlineRank(1)
            .frontlineDelta(-3)
            .build());

    analyzer.analyze(MATCH);

    DeathAnalysisDomain death = deathRepo.findByMatchId(MATCH).getFirst();
    assertThat(death.getFightIndex()).isZero();
    assertThat(death.getFrontlineDelta()).isEqualTo(-3);
  }

  @Test
  void deleteByMatchId_removesTheDeaths() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(deathRepo.findAll()).isEmpty();
  }

  private void stored() {
    for (int i = 0; i < 10; i++) {
      long slot = i < 5 ? i : 128L + i - 5;
      playerRepo.saveAndFlush(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .replayPlayerId(i * 2)
              .teamNum(i < 5 ? 2 : 3)
              .heroEntityName("Hero" + i)
              .build());
      int[] alive = new int[40];
      Arrays.fill(alive, i == 5 ? 0 : 1);
      trackRepo.saveAndFlush(
          ReplayPlayerTrackDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .startSeconds(0)
              .stepSeconds(5)
              .sampleCount(40)
              .x(filled(40, 100 * i))
              .y(filled(40, 0))
              .hpPercent(filled(40, 100))
              .manaPercent(filled(40, 100))
              .level(filled(40, 10))
              .flags(alive)
              .build());
    }
    stateRepo.saveAndFlush(
        ReplayStateChangeDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .gameSeconds(10.0)
            .kind("ITEM_ADDED")
            .name("item_black_king_bar")
            .slotIndex(0)
            .build());
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.DAMAGE,
                99,
                "npc_dota_hero_hero0",
                "npc_dota_hero_hero5",
                "",
                CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
                700,
                0,
                0,
                0,
                0,
                0,
                0),
            new CombatEvent(
                CombatEventKind.DEATH,
                100,
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
                0b11));
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
    Arrays.fill(values, value);
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
