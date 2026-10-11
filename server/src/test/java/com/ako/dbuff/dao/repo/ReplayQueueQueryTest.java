package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReplayQueueQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.now();
  private static final int VERSION = 1;

  @Autowired private MatchRepo matchRepo;
  @Autowired private ReplayParseRunRepo runRepo;

  @Test
  void findMatchesWithoutReplayRun_listsStoredRecentMatchesWithoutARunNewestFirst() {
    match(1L, 1_100L, NOW);
    match(2L, 1_300L, NOW);
    match(3L, 1_200L, null);
    match(4L, 500L, NOW);
    match(5L, 1_400L, NOW);
    MatchDomain noSalt = match(6L, 1_500L, NOW);
    noSalt.setReplaySalt(null);
    matchRepo.saveAndFlush(noSalt);
    MatchDomain otherMode = match(7L, 1_600L, NOW);
    otherMode.setGameModeId(3L);
    matchRepo.saveAndFlush(otherMode);
    run(5L, VERSION, ReplayStatus.DONE, null, null);
    run(2L, VERSION + 1, ReplayStatus.DONE, null, null);

    List<Long> ids =
        matchRepo
            .findMatchesWithoutReplayRun(VERSION, 1_000L, List.of(18L, 22L), PageRequest.of(0, 10))
            .stream()
            .map(MatchDomain::getId)
            .toList();

    assertThat(ids).containsExactly(2L, 1L);
  }

  @Test
  void findDueRetries_returnsTransientFailuresPastTheirBackoffAndAbandonedLeases() {
    run(1L, VERSION, ReplayStatus.FAILED_DOWNLOAD, NOW.minusMinutes(1), null);
    run(8L, VERSION, ReplayStatus.PARTIAL, NOW.minusMinutes(1), null);
    run(9L, VERSION, ReplayStatus.FAILED_TIMEOUT, NOW.minusMinutes(1), null);
    run(2L, VERSION, ReplayStatus.FAILED_DOWNLOAD, NOW.plusHours(1), null);
    run(3L, VERSION, ReplayStatus.FAILED_DOWNLOAD, null, null);
    run(4L, VERSION, ReplayStatus.RUNNING, null, NOW.minusMinutes(5));
    run(5L, VERSION, ReplayStatus.RUNNING, null, NOW.plusMinutes(5));
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(10L)
            .parserVersion(VERSION)
            .status(ReplayStatus.RUNNING)
            .attempts(5)
            .leaseUntil(NOW.minusMinutes(5))
            .build());
    run(6L, VERSION, ReplayStatus.EXPIRED, NOW.minusMinutes(1), null);
    run(7L, VERSION + 1, ReplayStatus.FAILED_RESOURCE, NOW.minusMinutes(1), null);

    List<Long> ids =
        runRepo.findDueRetries(VERSION, NOW, 5, PageRequest.of(0, 10)).stream()
            .map(ReplayParseRunDomain::getMatchId)
            .toList();

    assertThat(ids).containsExactly(1L, 8L, 9L, 4L);
  }

  private MatchDomain match(long id, long startTime, LocalDateTime detailsMappedAt) {
    MatchDomain match = new MatchDomain();
    match.setId(id);
    match.setStartTime(startTime);
    match.setDetailsMappedAt(detailsMappedAt);
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    match.setGameModeId(18L);
    match.setDuration(2400L);
    return matchRepo.saveAndFlush(match);
  }

  private void run(
      long matchId,
      int version,
      ReplayStatus status,
      LocalDateTime nextAttemptAt,
      LocalDateTime leaseUntil) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(matchId)
            .parserVersion(version)
            .status(status)
            .attempts(1)
            .nextAttemptAt(nextAttemptAt)
            .leaseUntil(leaseUntil)
            .build());
  }
}
