package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
            .findMatchesWithoutReplayRun(
                VERSION,
                1_000L,
                List.of(18L, 22L),
                NOW.minusDays(1),
                NOW.plusDays(1),
                Set.of(-1L),
                PageRequest.of(0, 10))
            .stream()
            .map(MatchDomain::getId)
            .toList();

    assertThat(ids).containsExactly(2L, 1L);
  }

  @Test
  void findMatchesWithoutReplayRun_limitsToTheMappedWindow() {
    match(1L, 1_100L, NOW.minusHours(1));
    match(2L, 1_200L, NOW.minusHours(10));
    match(3L, 1_300L, NOW.minusHours(30));

    assertThat(newIds(NOW.minusHours(6), NOW.plusDays(1))).containsExactly(1L);
    assertThat(newIds(NOW.minusHours(24), NOW.minusHours(6))).containsExactly(2L);
    assertThat(newIds(NOW.minusYears(1), NOW.minusHours(24))).containsExactly(3L);
  }

  @Test
  void dueRetryQueries_splitRetriesByMatchAgeAndAttempts() {
    LocalDateTime freshAfter = NOW.minusHours(6);
    Set<Long> none = Set.of(-1L);
    LocalDateTime youngAfter = NOW.minusHours(24);
    retry(1L, 1_100L, NOW.minusHours(1), 4);
    retry(2L, 1_200L, NOW.minusHours(10), 2);
    retry(3L, 1_300L, NOW.minusHours(10), 3);
    retry(4L, 1_400L, NOW.minusHours(30), 1);
    retry(5L, 1_500L, NOW.minusHours(12), 1);
    retry(6L, 1_600L, NOW.minusHours(2), 1);

    assertThat(
            ids(
                runRepo.findDueFreshRetries(
                    VERSION, NOW, 5, freshAfter, 0L, none, PageRequest.of(0, 10))))
        .containsExactly(6L, 1L);
    assertThat(
            ids(
                runRepo.findDueFreshRetries(
                    VERSION, NOW, 5, freshAfter, 1_450L, none, PageRequest.of(0, 10))))
        .containsExactly(6L);
    assertThat(
            ids(
                runRepo.findDueFreshRetries(
                    VERSION, NOW, 5, freshAfter, 0L, Set.of(6L), PageRequest.of(0, 10))))
        .containsExactly(1L);
    assertThat(
            ids(
                runRepo.findDueYoungRetries(
                    VERSION, NOW, 5, youngAfter, 2, none, PageRequest.of(0, 10))))
        .containsExactly(6L, 5L, 2L);
    assertThat(
            ids(
                runRepo.findDueOldRetries(
                    VERSION, NOW, 5, youngAfter, 2, none, PageRequest.of(0, 10))))
        .containsExactly(4L, 3L, 1L);
  }

  @Test
  void findDueFreshRetries_returnsTransientFailuresPastTheirBackoffAndAbandonedLeases() {
    for (long id = 1; id <= 10; id++) {
      match(id, 1_000L + id, NOW.minusHours(1));
    }
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
        ids(
            runRepo.findDueFreshRetries(
                VERSION, NOW, 5, NOW.minusHours(6), 0L, Set.of(-1L), PageRequest.of(0, 10)));

    assertThat(ids).containsExactly(9L, 8L, 4L, 1L);
  }

  private List<Long> newIds(LocalDateTime from, LocalDateTime before) {
    return matchRepo
        .findMatchesWithoutReplayRun(
            VERSION, 1_000L, List.of(18L, 22L), from, before, Set.of(-1L), PageRequest.of(0, 10))
        .stream()
        .map(MatchDomain::getId)
        .toList();
  }

  private static List<Long> ids(List<MatchDomain> matches) {
    return matches.stream().map(MatchDomain::getId).toList();
  }

  private void retry(long id, long startTime, LocalDateTime mappedAt, int attempts) {
    match(id, startTime, mappedAt);
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(id)
            .parserVersion(VERSION)
            .status(ReplayStatus.FAILED_DOWNLOAD)
            .attempts(attempts)
            .nextAttemptAt(NOW.minusMinutes(1))
            .build());
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
