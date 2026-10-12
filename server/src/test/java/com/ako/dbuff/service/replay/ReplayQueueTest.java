package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReplayQueueTest {

  private static final long HOUR = 3600;
  private final long nowSeconds = Instant.now().getEpochSecond();

  @Autowired private MatchRepo matchRepo;
  @Autowired private ReplayParseRunRepo runRepo;

  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private ReplayQueue queue;

  @BeforeEach
  void setUp() {
    queue = new ReplayQueue(matchRepo, runRepo, properties);
  }

  @Test
  void next_takesFreshMatchesNewestFirst() {
    match(1L, 5, 2);
    match(2L, 3, 1);
    match(3L, 1, 1);

    assertThat(drain(3)).containsExactly(3L, 2L, 1L);
  }

  @Test
  void next_putsFreshBeforeYoungRetryBeforeOlderNewMatches() {
    match(1L, 12, 12);
    match(2L, 20, 20);
    match(3L, 1, 1);
    match(4L, 15, 15);
    retry(4L, 1);

    assertThat(lanes(4))
        .containsExactly(
            entry(3L, Lane.FRESH),
            entry(4L, Lane.YOUNG_RETRY),
            entry(1L, Lane.NEW),
            entry(2L, Lane.NEW));
  }

  @Test
  void next_keepsADueRetryOfAFreshMatchInTheFreshLane_newestFirst() {
    match(1L, 4, 4);
    match(2L, 2, 2);
    match(3L, 1, 1);
    retry(1L, 4);
    retry(3L, 1);

    assertThat(lanes(3))
        .containsExactly(entry(3L, Lane.FRESH), entry(2L, Lane.FRESH), entry(1L, Lane.FRESH));
  }

  @Test
  void next_ignoresARetryThatIsNotDueYet() {
    match(1L, 12, 12);
    retry(1L, 1, LocalDateTime.now().plusHours(1));

    assertThat(queue.next(0, Set.of())).isEmpty();
  }

  @Test
  void next_demotesAYoungRetryWithTooManyAttemptsToTheBacklog() {
    match(1L, 12, 12);
    match(2L, 14, 14);
    retry(1L, 3);

    assertThat(drain(5)).containsExactly(2L);
    properties.getBacklog().setEnabled(true);
    assertThat(queue.next(1, Set.of()).map(QueuedMatch::match).map(MatchDomain::getId))
        .contains(1L);
  }

  @Test
  void next_leavesOldRetriesAndBacklogOutWhileTheBacklogIsOff() {
    match(1L, 30, 30);
    match(2L, 40, 40);
    retry(2L, 1);

    assertThat(queue.next(5, Set.of())).isEmpty();
  }

  @Test
  void next_takesOldRetriesThenBacklogWhenOnAndNothingElseIsWaiting() {
    properties.getBacklog().setEnabled(true);
    match(1L, 30, 30);
    match(2L, 40, 40);
    match(3L, 50, 50);
    retry(3L, 1);
    match(4L, 1, 1);

    assertThat(lanes(4))
        .containsExactly(
            entry(4L, Lane.FRESH),
            entry(3L, Lane.BACKLOG),
            entry(1L, Lane.BACKLOG),
            entry(2L, Lane.BACKLOG));
  }

  @Test
  void next_takesNoBacklogWhenTheRunHasNoRoomLeft() {
    properties.getBacklog().setEnabled(true);
    match(1L, 30, 30);

    assertThat(queue.next(0, Set.of())).isEmpty();
  }

  @Test
  void next_takesAFreshMatchThatArrivesMidBatchBeforeTheOlderOnesWaiting() {
    match(1L, 12, 12);
    match(2L, 14, 14);

    assertThat(queue.next(0, Set.of()).orElseThrow().match().getId()).isEqualTo(1L);
    match(3L, 0, 0);
    assertThat(queue.next(0, Set.of()).orElseThrow().match().getId()).isEqualTo(3L);
  }

  @Test
  void next_skipsMatchesThatAlreadyHaveARun() {
    match(1L, 1, 1);
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(1L)
            .parserVersion(properties.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .build());

    assertThat(queue.next(0, Set.of())).isEmpty();
  }

  @Test
  void next_neverHandsOutAnExcludedMatch() {
    match(1L, 1, 1);
    match(2L, 2, 2);

    assertThat(queue.next(0, Set.of(1L)).orElseThrow().match().getId()).isEqualTo(2L);
    assertThat(queue.next(0, Set.of(1L, 2L))).isEmpty();
  }

  @Test
  void next_leavesFreshLaneToMatchesThatStartedRecentlyEvenWhenMappedLately() {
    match(1L, 30, 1);
    match(2L, 12, 12);

    assertThat(lanes(2)).containsExactly(entry(2L, Lane.NEW), entry(1L, Lane.NEW));
  }

  @Test
  void next_keepsARetryOfAnOldMatchMappedLatelyOutOfTheFreshLane() {
    match(1L, 30, 1);
    retry(1L, 1);

    assertThat(lanes(1)).containsExactly(entry(1L, Lane.YOUNG_RETRY));
  }

  private List<Long> drain(int max) {
    return lanes(max).stream().map(Entry::id).toList();
  }

  private List<Entry> lanes(int max) {
    List<Entry> taken = new ArrayList<>();
    for (int i = 0; i < max; i++) {
      Optional<QueuedMatch> next = queue.next(max, Set.of());
      if (next.isEmpty()) {
        break;
      }
      long id = next.get().match().getId();
      taken.add(new Entry(id, next.get().lane()));
      markDone(id);
    }
    return taken;
  }

  private void markDone(long id) {
    ReplayParseRunDomain run =
        runRepo
            .findByMatchIdAndParserVersion(id, properties.getParserVersion())
            .orElseGet(
                () ->
                    ReplayParseRunDomain.builder()
                        .matchId(id)
                        .parserVersion(properties.getParserVersion())
                        .attempts(1)
                        .build());
    run.setStatus(ReplayStatus.DONE);
    run.setNextAttemptAt(null);
    runRepo.saveAndFlush(run);
  }

  private static Entry entry(long id, Lane lane) {
    return new Entry(id, lane);
  }

  private record Entry(long id, Lane lane) {}

  private void match(long id, int startedHoursAgo, int mappedHoursAgo) {
    MatchDomain match = new MatchDomain();
    match.setId(id);
    match.setStartTime(nowSeconds - startedHoursAgo * HOUR);
    match.setDetailsMappedAt(LocalDateTime.now().minusHours(mappedHoursAgo).plusMinutes(1));
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    match.setGameModeId(18L);
    match.setDuration(2400L);
    matchRepo.saveAndFlush(match);
  }

  private void retry(long matchId, int attempts) {
    retry(matchId, attempts, LocalDateTime.now().minusMinutes(1));
  }

  private void retry(long matchId, int attempts, LocalDateTime nextAttemptAt) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(matchId)
            .parserVersion(properties.getParserVersion())
            .status(ReplayStatus.FAILED_DOWNLOAD)
            .attempts(attempts)
            .nextAttemptAt(nextAttemptAt)
            .build());
  }
}
