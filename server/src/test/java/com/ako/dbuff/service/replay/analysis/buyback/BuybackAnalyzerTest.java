package com.ako.dbuff.service.replay.analysis.buyback;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
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
  BuybackAnalyzer.class,
  CombatLogReader.class,
  ReplayProcessingProperties.class,
  BuybackAnalyzerTest.Json.class
})
class BuybackAnalyzerTest {

  private static final long MATCH = 13L;

  @Autowired private BuybackAnalyzer analyzer;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private BuybackEventRepo buybackRepo;

  @Test
  void analyze_tiesABuybackToTheDeathAndTheFightItFollowed_andReplacesAnEarlierRun() {
    stored(130);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<BuybackEventDomain> rows = buybackRepo.findByMatchId(MATCH);
    assertThat(rows).hasSize(1);
    BuybackEventDomain row = rows.getFirst();
    assertThat(row.getPlayerSlot()).isEqualTo(128L);
    assertThat(row.getDeathSeconds()).isEqualTo(100.0);
    assertThat(row.getSecondsDead()).isEqualTo(30.0);
    assertThat(row.getFightIndex()).isZero();
    assertThat(row.getFightWon()).as("Radiant won, the buyer is Dire").isFalse();
  }

  @Test
  void analyze_keepsABuybackWithNoDeathNearItWithoutAFight() {
    stored(400);

    analyzer.analyze(MATCH);

    BuybackEventDomain row = buybackRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getDeathSeconds()).isNull();
    assertThat(row.getFightIndex()).isNull();
    assertThat(row.getFightWon()).isNull();
  }

  @Test
  void deleteByMatchId_removesTheBuybacks() {
    stored(130);
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(buybackRepo.findAll()).isEmpty();
  }

  private void stored(double buybackAt) {
    for (int i = 0; i < 10; i++) {
      playerRepo.saveAndFlush(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(i < 5 ? (long) i : 128L + i - 5)
              .replayPlayerId(i * 2)
              .teamNum(i < 5 ? 2 : 3)
              .heroEntityName("Hero" + i)
              .build());
    }
    fightRepo.saveAndFlush(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .type("SKIRMISH")
            .startSeconds(95.0)
            .endSeconds(105.0)
            .winner("RADIANT")
            .build());
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.DEATH,
                100,
                "npc_dota_hero_hero0",
                "npc_dota_hero_hero5",
                "",
                CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
                0, 0, 0, 0, 0, 0, 1),
            new CombatEvent(
                CombatEventKind.BUYBACK, buybackAt, "", "", "", 0, 5, 0, 0, 0, 0, 0, 0));
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
