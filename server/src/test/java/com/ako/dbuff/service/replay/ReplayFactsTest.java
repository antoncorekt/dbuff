package com.ako.dbuff.service.replay;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayFactsTest {

  @Test
  void playersById_keepsTheFirstEntryOfAPlayerListedTwice() {
    ReplayFacts facts =
        ReplayFacts.builder()
            .clockStartTick(1)
            .gameStartTime(1.0)
            .players(
                List.of(
                    new ReplayPlayerFact(0, 2, 1L, "Sniper"),
                    new ReplayPlayerFact(0, 2, 2L, "Axe"),
                    new ReplayPlayerFact(10, 3, 3L, "Sven")))
            .build();

    var byId = facts.playersById();

    assertThat(byId).hasSize(2);
    assertThat(byId.get(0).heroEntityName()).isEqualTo("Sniper");
  }
}
