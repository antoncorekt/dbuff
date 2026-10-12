package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Hands out the match the replay job takes next. Lanes, in order: matches that started and were
 * mapped lately (new ones and their due retries, newest first), young retries with few attempts,
 * other new matches mapped within the retry priority window, and last, only when enabled, old
 * retries and the backlog of matches mapped earlier than that.
 */
@Component
@RequiredArgsConstructor
public class ReplayQueue {
  private static final Pageable ONE = PageRequest.of(0, 1);
  private static final Collection<Long> NONE = Set.of(-1L);
  private static final LocalDateTime FOREVER = LocalDateTime.of(9999, 12, 31, 0, 0);
  private static final LocalDateTime LONG_AGO = LocalDateTime.of(1970, 1, 1, 0, 0);

  private final MatchRepo matchRepo;
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;
  private final GameModeRegistry modes;

  /**
   * @param backlogRoom how many backlog matches this job run may still take
   * @param attempted matches this run already took; never handed out again
   */
  public Optional<QueuedMatch> next(int backlogRoom, Collection<Long> attempted) {
    Set<Long> activeModes = modes.activeModeIds();
    if (activeModes.isEmpty()) {
      return Optional.empty();
    }
    Collection<Long> excluded = attempted.isEmpty() ? NONE : attempted;
    LocalDateTime now = LocalDateTime.now();
    Window window =
        new Window(
            now,
            now.minusHours(properties.getFreshWindowHours()),
            now.minusHours(properties.getRetryPriorityHours()),
            excluded,
            activeModes);
    Optional<QueuedMatch> next =
        firstOf(
            () -> fresh(window),
            () -> tag(Lane.YOUNG_RETRY, youngRetry(window)),
            () -> tag(Lane.NEW, newMatch(window, window.youngAfter(), FOREVER, maxAgeLimit())));
    if (next.isEmpty() && properties.getBacklog().isEnabled() && backlogRoom > 0) {
      next =
          firstOf(
              () -> tag(Lane.BACKLOG, oldRetry(window)),
              () ->
                  tag(
                      Lane.BACKLOG,
                      newMatch(window, LONG_AGO, window.youngAfter(), maxAgeLimit())));
    }
    return next;
  }

  private Optional<QueuedMatch> fresh(Window window) {
    long startedAfter =
        Instant.now().minus(properties.getFreshWindowHours(), ChronoUnit.HOURS).getEpochSecond();
    Optional<MatchDomain> retry =
        first(
            runRepo.findDueFreshRetries(
                properties.getParserVersion(),
                window.now(),
                properties.getMaxAttempts(),
                window.freshAfter(),
                startedAfter,
                window.excluded(),
                ONE));
    Optional<MatchDomain> unseen = newMatch(window, window.freshAfter(), FOREVER, startedAfter);
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
            window.youngAfter(),
            properties.getRetryPriorityAttempts(),
            window.excluded(),
            ONE));
  }

  private Optional<MatchDomain> oldRetry(Window window) {
    return first(
        runRepo.findDueOldRetries(
            properties.getParserVersion(),
            window.now(),
            properties.getMaxAttempts(),
            window.youngAfter(),
            properties.getRetryPriorityAttempts(),
            window.excluded(),
            ONE));
  }

  private Optional<MatchDomain> newMatch(
      Window window, LocalDateTime mappedFrom, LocalDateTime mappedBefore, long startedAfter) {
    return first(
        matchRepo.findMatchesWithoutReplayRun(
            properties.getParserVersion(),
            startedAfter,
            window.modes(),
            mappedFrom,
            mappedBefore,
            window.excluded(),
            ONE));
  }

  private long maxAgeLimit() {
    return Instant.now().minus(properties.getMaxAgeDays(), ChronoUnit.DAYS).getEpochSecond();
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

  private record Window(
      LocalDateTime now,
      LocalDateTime freshAfter,
      LocalDateTime youngAfter,
      Collection<Long> excluded,
      Collection<Long> modes) {}
}
