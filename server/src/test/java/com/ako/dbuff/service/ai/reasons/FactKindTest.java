package com.ako.dbuff.service.ai.reasons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactKindTest {

  @Test
  void idsMapToTheirKind() {
    assertThat(FactKind.ofId("RESULT")).contains(FactKind.RESULT);
    assertThat(FactKind.ofId("F3")).contains(FactKind.FIGHT);
    assertThat(FactKind.ofId("LANE_BOTTOM")).contains(FactKind.LANE);
    assertThat(FactKind.ofId("STATS_FACELESS_VOID")).contains(FactKind.STATS);
    assertThat(FactKind.ofId("OBJ_2")).contains(FactKind.OBJECTIVE);
    assertThat(FactKind.ofId("FOO")).isEmpty();
    assertThat(FactKind.ofId("F")).isEmpty();
    assertThat(FactKind.ofId(null)).isEmpty();
  }

  @Test
  void numberedIdsFollowTheirKind() {
    assertThat(FactKind.FIGHT.id(3)).isEqualTo("F3");
    assertThat(FactKind.LANE.id(1)).isEqualTo("LANE_1");
    assertThat(FactKind.RESULT.id("")).isEqualTo("RESULT");
  }

  @Test
  void theKeptOrderIsResultFightsLanesObjectivesEndgameThenTheRest() {
    assertThat(FactKind.RESULT.weight()).isGreaterThan(FactKind.FIGHT.weight());
    assertThat(FactKind.FIGHT.weight()).isGreaterThan(FactKind.LANE.weight());
    assertThat(FactKind.LANE.weight()).isGreaterThan(FactKind.OBJECTIVE.weight());
    assertThat(FactKind.SWING.weight()).isEqualTo(FactKind.OBJECTIVE.weight());
    assertThat(FactKind.OBJECTIVE.weight()).isGreaterThan(FactKind.ENDGAME.weight());
    assertThat(FactKind.ENDGAME.weight()).isGreaterThan(FactKind.VISION.weight());
  }
}
