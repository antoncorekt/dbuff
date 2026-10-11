package com.ako.dbuff.service.match.backfill;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.match.DotaApiMatchDetailsService;
import com.ako.dbuff.service.match.data.MatchDetailService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Fills the OpenDota detail tables for matches stored before they existed. It does not retry within
 * a run: a problem with one match is recorded as an attempt and skipped, and only a rate limit or
 * an outage ends the run, so the job never holds a thread through one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchDetailBackfillService {

  private static final int TOO_MANY_REQUESTS = 429;
  private static final int SERVER_ERROR = 500;

  private final MatchRepo matchRepo;
  private final DotaApiMatchDetailsService dotaApiMatchDetailsService;
  private final MatchDetailService matchDetailService;
  private final MatchDetailBackfillProperties properties;

  /**
   * @return number of matches whose detail rows were stored in this run
   */
  public int backfillBatch() {
    LocalDateTime now = LocalDateTime.now();
    long recentAfter =
        now.minusDays(properties.getRetryWithinDays())
            .atZone(ZoneId.systemDefault())
            .toEpochSecond();
    List<MatchDomain> pending =
        matchRepo.findMatchesWithoutDetails(
            now.minusHours(properties.getRetryAfterHours()),
            recentAfter,
            PageRequest.of(0, properties.getBatchSize()));
    int stored = 0;
    for (MatchDomain match : pending) {
      MatchResponse response;
      try {
        response = dotaApiMatchDetailsService.fetchOnce(match.getId());
      } catch (ApiException e) {
        if (isRateLimitOrOutage(e)) {
          log.warn("Backfill stopped at match {}: {}", match.getId(), e.getMessage());
          break;
        }
        log.info("Backfill skips match {}: {}", match.getId(), e.getMessage());
        recordAttempt(match);
        continue;
      }
      try {
        matchDetailService.replace(response, match);
      } catch (RuntimeException e) {
        log.warn("Backfill could not store match {}: {}", match.getId(), e.getMessage(), e);
        recordAttempt(match);
        continue;
      }
      persistState(match);
      if (match.getDetailsMappedAt() != null) {
        stored++;
      }
    }
    if (!pending.isEmpty()) {
      log.info("Match detail backfill: {} of {} matches stored", stored, pending.size());
    }
    return stored;
  }

  private static boolean isRateLimitOrOutage(ApiException e) {
    return e.getCode() == TOO_MANY_REQUESTS || e.getCode() >= SERVER_ERROR || e.getCode() == 0;
  }

  private void recordAttempt(MatchDomain match) {
    match.setDetailsAttemptedAt(LocalDateTime.now());
    persistState(match);
  }

  /** Writes only the bookkeeping columns, so a snapshot loaded earlier cannot undo other jobs. */
  private void persistState(MatchDomain match) {
    matchRepo.updateDetailState(
        match.getId(),
        match.getHasParsed(),
        match.getReplayCluster(),
        match.getReplaySalt(),
        match.getDetailsMappedAt(),
        match.getDetailsAttemptedAt());
  }
}
