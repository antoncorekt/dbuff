package com.ako.dbuff.service.replay.report;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviourWordingTest {

  @Test
  void heroNeverThrowsForANullMapOrASlotOutsideBothTeams() {
    assertThat(BehaviourWording.hero(null, 130L)).isEqualTo("Dire player 3");
    assertThat(BehaviourWording.hero(null, 0L)).isEqualTo("Radiant player 1");
    assertThat(BehaviourWording.hero(Map.of(), -1L)).isEqualTo("player -1");
    assertThat(BehaviourWording.hero(Map.of(), 5L)).isEqualTo("player 5");
    assertThat(BehaviourWording.hero(Map.of(), 133L)).isEqualTo("player 133");
    assertThat(BehaviourWording.hero(Map.of(), 100000L)).isEqualTo("player 100000");
    assertThat(BehaviourWording.hero(Map.of(2L, "Axe"), 2L)).isEqualTo("Axe");
    assertThat(BehaviourWording.hero(Map.of(), null)).isEqualTo("?");
  }

  @Test
  void tripWonUsesPluralCorrectWords() {
    assertThat(BehaviourWording.tripWon(null, null)).isEmpty();
    assertThat(BehaviourWording.tripWon(0, 0)).isEmpty();
    assertThat(BehaviourWording.tripWon(1, null)).isEqualTo("killed 1");
    assertThat(BehaviourWording.tripWon(null, 1)).isEqualTo("won the fight");
    assertThat(BehaviourWording.tripWon(2, 2)).isEqualTo("killed 2 and won 2 fights");
  }
}
