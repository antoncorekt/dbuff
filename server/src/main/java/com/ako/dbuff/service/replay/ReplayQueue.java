package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Hands out the match the replay job takes next. Lanes, in order: matches whose details were mapped
 * lately (new ones and their due retries, newest first), young retries with few attempts, other new
 * matches mapped within the retry priority window, and last, only when enabled, old retries and the
 * backlog of matches mapped earlier than that.
 */
@Component
@RequiredArgsConstructor
public class ReplayQueue {
  private static final Pageable ONE = PageRequest.of(0, 1);
  private static final LocalDateTime LONG_AGO = LocalDateTime.of(1970, 1, 1, 0, 0);

  private final MatchRepo matchRepo;
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;

  /**
   * @param backlogRoom how many backlog matches this job run may still take
   */
  public Optional<QueuedMatch> next(int backlogRoom) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime freshAfter = now.minusHours(properties.getFreshWindowHours());
    LocalDateTime youngAfter = now.minusHours(properties.getRetryPriorityHours());
    Window window = new Window(now, freshAfter, youngAfter);
    Optional<QueuedMatch> next =
        firstOf(
            () -> fresh(window),
            () -> tag(Lane.YOUNG_RETRY, youngRetry(window)),
            () -> tag(Lane.NEW, newMatch(youngAfter, freshAfter)));
    if (next.isEmpty() && properties.getBacklog().isEnabled() && backlogRoom > 0) {
      next =
          firstOf(
              () -> tag(Lane.BACKLOG, oldRetry(window)),
              () -> tag(Lane.BACKLOG, newMatch(LONG_AGO, youngAfter)));
    }
    return next;
  }

  private Optional<QueuedMatch> fresh(Window window) {
    Optional<MatchDomain> retry =
        first(
            runRepo.findDueFreshRetries(
                properties.getParserVersion(),
                window.now(),
                properties.getMaxAttempts(),
                window.freshAfter(),
                ONE));
    Optional<MatchDomain> unseen = newMatch(window.freshAfter(), window.now().plusYears(1));
    return Stream.of(retry, unseen)
        .flatMap(Optional::stream)
        .max(Comparator.comparing(MatchDomain::getStartTime))
        .map(match -> new QueuedMatch(match, Lane.FRESH));
  }

  private Optional<MatchDomain> youngRetry(Window window) {
    return first(
        runRepo.findDueYoungRetries(
            properties.getParserVersion(),
            window.now(),
            properties.getMaxAttempts(),
            window.freshAfter(),
            window.youngAfter(),
            properties.getRetryPriorityAttempts(),
            ONE));
  }

  private Optional<MatchDomain> oldRetry(Window window) {
    return first(
        runRepo.findDueOldRetries(
            properties.getParserVersion(),
            window.now(),
            properties.getMaxAttempts(),
            window.freshAfter(),
            window.youngAfter(),
            properties.getRetryPriorityAttempts(),
            ONE));
  }

  private Optional<MatchDomain> newMatch(LocalDateTime mappedFrom, LocalDateTime mappedBefore) {
    long recentAfter =
        Instant.now().minus(properties.getMaxAgeDays(), ChronoUnit.DAYS).getEpochSecond();
    return first(
        matchRepo.findMatchesWithoutReplayRun(
            properties.getParserVersion(),
            recentAfter,
            properties.getSupportedModes(),
            mappedFrom,
            mappedBefore,
            ONE));
  }

  @SafeVarargs
  private static Optional<QueuedMatch> firstOf(Supplier<Optional<QueuedMatch>>... lanes) {
    for (Supplier<Optional<QueuedMatch>> lane : lanes) {
      Optional<QueuedMatch> found = lane.get();
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Optional<QueuedMatch> tag(Lane lane, Optional<MatchDomain> match) {
    return match.map(m -> new QueuedMatch(m, lane));
  }

  private static <T> Optional<T> first(List<T> list) {
    return list.stream().findFirst();
  }

  private record Window(LocalDateTime now, LocalDateTime freshAfter, LocalDateTime youngAfter) {}
}
