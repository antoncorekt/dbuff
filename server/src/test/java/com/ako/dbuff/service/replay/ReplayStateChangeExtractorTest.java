package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ReplayStateChangeExtractor.class)
class ReplayStateChangeExtractorTest {

  private static final MatchDomain MATCH = match();

  @Autowired private ReplayStateChangeExtractor extractor;
  @Autowired private ReplayStateChangeRepo repo;

  @Test
  void extract_storesTheChangesOnOpenDotaSlotsInTimeOrderAndReplacesAnEarlierRun() {
    ReplayFacts facts = facts();

    extractor.extract(facts, MATCH);
    extractor.extract(facts, MATCH);

    List<ReplayStateChangeDomain> rows = repo.findByMatchIdOrderByGameSeconds(MATCH.getId());
    assertThat(rows).hasSize(3);
    assertThat(rows).extracting(ReplayStateChangeDomain::getGameSeconds).isSorted();
    ReplayStateChangeDomain cooldown =
        rows.stream().filter(r -> r.getKind().equals("COOLDOWN")).findFirst().orElseThrow();
    assertThat(cooldown.getPlayerSlot()).isEqualTo(128L);
    assertThat(cooldown.getReadyAtSeconds()).isEqualTo(130.0);
    assertThat(cooldown.getCooldownLength()).isEqualTo(30.0);
  }

  @Test
  void extract_ignoresChangesOfAPlayerNotOnTheRoster_andStoresNothingWithoutState() {
    extractor.extract(
        ReplayFacts.builder()
            .players(List.of(new ReplayPlayerFact(0, 2, 0L, "Sniper")))
            .stateChanges(
                new StateFacts(
                    List.of(
                        new StateChange(
                            4, 5, StateChangeKind.ITEM_ADDED, "item_tango", 0, 0, 0, 0))))
            .build(),
        MATCH);
    assertThat(repo.findAll()).isEmpty();

    extractor.extract(ReplayFacts.builder().players(List.of()).build(), MATCH);
    assertThat(repo.findAll()).isEmpty();
  }

  private static ReplayFacts facts() {
    return ReplayFacts.builder()
        .players(
            List.of(
                new ReplayPlayerFact(0, 2, 0L, "Sniper"), new ReplayPlayerFact(10, 3, 0L, "Sven")))
        .stateChanges(
            new StateFacts(
                List.of(
                    new StateChange(0, -85, StateChangeKind.ITEM_ADDED, "item_tango", 0, 0, 0, 0),
                    new StateChange(
                        10, 100, StateChangeKind.COOLDOWN, "sven_storm_bolt", 1, 130, 30, 0),
                    new StateChange(
                        0, 205, StateChangeKind.ABILITY_LEVEL, "sniper_take_aim", 2, 0, 0, 2))))
        .build();
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(6L);
    return match;
  }
}
