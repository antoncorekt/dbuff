package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLoopTopicTest {
  @Test
  void theCommittedListStartsWithTheAbilityDraftFixtures() {
    RoleLoopTopic topic = new RoleLoopTopic(null, null, null, null, null, null, null, null);

    assertThat(ReviewLoopMatchList.read(topic.matchList()))
        .containsExactly(9038725154L, 9037038717L, 9040892342L, 9040889566L);
  }
}
