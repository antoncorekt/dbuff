package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AiReasonsStatus;
import com.ako.dbuff.dao.model.MatchAiReasonsDomain;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MatchAiReasonsRepoTest {

  @Autowired private MatchAiReasonsRepo repo;

  private MatchAiReasonsDomain row(
      long match, String hash, AiReasonsStatus status, LocalDateTime at) {
    return repo.saveAndFlush(
        MatchAiReasonsDomain.builder()
            .matchId(match)
            .side("DIRE")
            .factsHash(hash)
            .reasonsJson(status == AiReasonsStatus.DONE ? "[]" : null)
            .status(status)
            .createdAt(at)
            .build());
  }

  @Test
  void reuseFindsTheNewestDoneRowOfTheSameFactsOnly() {
    LocalDateTime now = LocalDateTime.now();
    row(1L, "h", AiReasonsStatus.DONE, now.minusHours(2));
    MatchAiReasonsDomain newest = row(1L, "h", AiReasonsStatus.DONE, now.minusHours(1));
    row(1L, "h", AiReasonsStatus.FAILED, now);
    row(1L, "other", AiReasonsStatus.DONE, now);

    assertThat(
            repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
                1L, "DIRE", "h", AiReasonsStatus.DONE))
        .map(MatchAiReasonsDomain::getId)
        .contains(newest.getId());
    assertThat(
            repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
                1L, "RADIANT", "h", AiReasonsStatus.DONE))
        .isEmpty();
  }

  @Test
  void theDailyCountIncludesFailedCallsOfThatMatchOnly() {
    LocalDateTime now = LocalDateTime.now();
    row(2L, "a", AiReasonsStatus.DONE, now.minusHours(3));
    row(2L, "b", AiReasonsStatus.FAILED, now.minusHours(1));
    row(2L, "c", AiReasonsStatus.DONE, now.minusDays(2));
    row(3L, "a", AiReasonsStatus.DONE, now);

    assertThat(repo.countByMatchIdAndCreatedAtAfter(2L, now.minusDays(1))).isEqualTo(2);
  }
}
