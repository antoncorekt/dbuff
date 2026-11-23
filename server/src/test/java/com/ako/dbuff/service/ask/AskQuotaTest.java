package com.ako.dbuff.service.ask;

import com.ako.dbuff.dao.model.AskThreadDomain;
import com.ako.dbuff.dao.model.AskTurnDomain;
import com.ako.dbuff.dao.repo.AskThreadRepo;
import com.ako.dbuff.dao.repo.AskTurnRepo;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AskQuotaTest {

  private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");

  @Autowired private AskThreadRepo threadRepo;
  @Autowired private AskTurnRepo turnRepo;

  private AskProperties properties;
  private AskQuota quota;

  @BeforeEach
  void setUp() {
    properties = new AskProperties();
    properties.setMaxQuestionsPerChannelPerDay(2);
    properties.setMaxTurnsPerThread(2);
    quota = new AskQuota(turnRepo, properties);
    threadRepo.save(thread("t1", "inst-a"));
    threadRepo.save(thread("t2", "inst-b"));
  }

  @Test
  void capReachedAfterTodaysQuestions() {
    assertThat(quota.canAsk("inst-a", NOW)).isTrue();
    question("t1", 0, NOW.minusSeconds(60));
    assertThat(quota.canAsk("inst-a", NOW)).isTrue();
    question("t1", 2, NOW.minusSeconds(30));

    assertThat(quota.canAsk("inst-a", NOW)).isFalse();
    assertThat(quota.canAsk("inst-b", NOW)).isTrue();
  }

  @Test
  void yesterdayAndToolResultsAreNotCounted() {
    question("t1", 0, Instant.parse("2026-11-21T23:59:59Z"));
    question("t1", 2, Instant.parse("2026-11-21T10:00:00Z"));
    turnRepo.save(turn("t1", 3, false, NOW));

    assertThat(quota.canAsk("inst-a", NOW)).isTrue();
  }

  @Test
  void threadFullAfterMaxUserTurns() {
    question("t2", 0, NOW);
    assertThat(quota.threadFull("t2")).isFalse();
    question("t2", 2, NOW);

    assertThat(quota.threadFull("t2")).isTrue();
  }

  private void question(String threadId, int seq, Instant at) {
    turnRepo.save(turn(threadId, seq, true, at));
  }

  private static AskTurnDomain turn(String threadId, int seq, boolean userQuestion, Instant at) {
    return AskTurnDomain.builder()
        .threadId(threadId)
        .seq(seq)
        .role("user")
        .contentJson("{}")
        .userQuestion(userQuestion)
        .createdAt(at)
        .build();
  }

  private static AskThreadDomain thread(String id, String instanceId) {
    return AskThreadDomain.builder()
        .threadId(id)
        .instanceId(instanceId)
        .channelId("ch")
        .starterDiscordUserId("u")
        .createdAt(NOW)
        .lastActivityAt(NOW)
        .build();
  }
}
