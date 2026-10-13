package com.ako.dbuff.service.replay.visionloop;

import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionLoopTopicTest {
  @Test
  void theCommittedListHoldsTheTwoAbilityDraftFixtures() {
    assertThat(ReviewLoopMatchList.read(VisionLoopTopic.MATCH_LIST))
        .containsExactly(9038725154L, 9037038717L);
  }
}
