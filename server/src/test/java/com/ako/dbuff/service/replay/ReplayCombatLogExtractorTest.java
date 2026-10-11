package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReplayCombatLogExtractor.class, CombatLogReader.class})
class ReplayCombatLogExtractorTest {

  private static final MatchDomain MATCH = match();

  @Autowired private ReplayCombatLogExtractor extractor;
  @Autowired private CombatLogReader reader;
  @Autowired private ReplayCombatLogRepo repo;

  @Test
  void extract_storesTheLogAsOneBlobTheReaderGivesBackAndReplacesAnEarlierRun() {
    CombatEvent hit =
        new CombatEvent(
            CombatEventKind.DAMAGE,
            5.0,
            "npc_dota_hero_axe",
            "npc_dota_hero_sven",
            "",
            3,
            90,
            400,
            0,
            0,
            0,
            0,
            0);

    extractor.extract(facts(List.of(hit)), MATCH);
    extractor.extract(facts(List.of(hit, hit)), MATCH);

    assertThat(repo.findAll()).hasSize(1);
    assertThat(repo.findAll().getFirst().getEventCount()).isEqualTo(2);
    assertThat(reader.events(MATCH.getId())).containsExactly(hit, hit);
  }

  @Test
  void extract_storesNothingWhenThereWasNoCombatLog_andTheReaderReturnsAnEmptyList() {
    extractor.extract(
        ReplayFacts.builder().clockStartTick(1).gameStartTime(1.0).players(List.of()).build(),
        MATCH);
    extractor.extract(facts(List.of()), MATCH);

    assertThat(repo.findAll()).isEmpty();
    assertThat(reader.events(MATCH.getId())).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheBlob() {
    extractor.extract(
        facts(
            List.of(
                new CombatEvent(
                    CombatEventKind.BUYBACK,
                    1.0,
                    "",
                    "npc_dota_hero_axe",
                    "",
                    2,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0))),
        MATCH);

    extractor.deleteByMatchId(MATCH.getId());

    assertThat(repo.findAll()).isEmpty();
  }

  private static ReplayFacts facts(List<CombatEvent> events) {
    return ReplayFacts.builder()
        .clockStartTick(1)
        .gameStartTime(1.0)
        .players(List.of())
        .combatLog(new CombatLogFacts(events))
        .build();
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(4L);
    return match;
  }
}
