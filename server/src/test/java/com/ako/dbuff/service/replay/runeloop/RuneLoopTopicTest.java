package com.ako.dbuff.service.replay.runeloop;

import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuneLoopTopicTest {
  @Test
  void theCommittedListHoldsTheTwoAbilityDraftFixtures() {
    assertThat(ReviewLoopMatchList.read(RuneLoopTopic.MATCH_LIST))
        .containsExactly(9038725154L, 9037038717L);
  }
}
