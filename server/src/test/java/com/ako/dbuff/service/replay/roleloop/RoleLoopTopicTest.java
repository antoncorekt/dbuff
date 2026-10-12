package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLoopTopicTest {
  @Test
  void theCommittedListStartsWithTheAbilityDraftFixtures() {
    assertThat(ReviewLoopMatchList.read(RoleLoopTopic.MATCH_LIST))
        .containsExactly(9038725154L, 9037038717L, 9040892342L, 9040889566L);
  }
}
