package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ReplayPlayerExtractor.class)
class ReplayPlayerExtractorTest {

  @Autowired private ReplayPlayerExtractor extractor;
  @Autowired private ReplayPlayerRepo repo;

  @Test
  void extract_storesThePlayersOnOpenDotaSlotsAndReplacesAnEarlierRun() {
    MatchDomain match = new MatchDomain();
    match.setId(5L);
    ReplayFacts first =
        ReplayFacts.builder()
            .clockStartTick(100)
            .gameStartTime(1.0)
            .players(
                List.of(
                    new ReplayPlayerFact(0, 2, 76561197960265728L + 7, "Sniper"),
                    new ReplayPlayerFact(10, 3, 0L, "Sven")))
            .build();

    extractor.extract(first, match);
    extractor.extract(
        ReplayFacts.builder()
            .clockStartTick(100)
            .gameStartTime(1.0)
            .players(List.of(new ReplayPlayerFact(10, 3, 0L, "Axe")))
            .build(),
        match);

    List<ReplayPlayerDomain> stored = repo.findAll();
    assertThat(stored).hasSize(1);
    assertThat(stored.getFirst().getPlayerSlot()).isEqualTo(128L);
    assertThat(stored.getFirst().getAccountId()).isNull();
    assertThat(stored.getFirst().getHeroEntityName()).isEqualTo("Axe");
  }

  @Test
  void deleteByMatchId_removesOnlyThatMatch() {
    MatchDomain one = new MatchDomain();
    one.setId(1L);
    MatchDomain two = new MatchDomain();
    two.setId(2L);
    ReplayFacts facts =
        ReplayFacts.builder()
            .clockStartTick(100)
            .gameStartTime(1.0)
            .players(List.of(new ReplayPlayerFact(0, 2, 0L, "Sniper")))
            .build();
    extractor.extract(facts, one);
    extractor.extract(facts, two);

    extractor.deleteByMatchId(1L);

    assertThat(repo.findAll()).extracting(ReplayPlayerDomain::getMatchId).containsExactly(2L);
  }
}
