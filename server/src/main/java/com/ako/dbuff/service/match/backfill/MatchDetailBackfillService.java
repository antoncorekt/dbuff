package com.ako.dbuff.service.match.backfill;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.match.data.MatchDetailService;
import com.google.common.util.concurrent.RateLimiter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Fills the OpenDota detail tables for matches stored before they existed. Deliberately does not
 * retry or back off: a failed request ends the run and the next scheduled run picks up the same
 * matches, which keeps the job from holding a thread through an outage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchDetailBackfillService {

  private static final int NOT_FOUND = 404;

  private final MatchRepo matchRepo;
  private final MatchesApi matchesApi;
  private final RateLimiter dotaApiRateLimiter;
  private final MatchDetailService matchDetailService;
  private final MatchDetailBackfillProperties properties;

  /**
   * @return number of matches that no longer need a backfill after this run
   */
  public int backfillBatch() {
    List<MatchDomain> pending =
        matchRepo.findMatchesWithoutDetails(PageRequest.of(0, properties.getBatchSize()));
    int completed = 0;
    for (MatchDomain match : pending) {
      MatchResponse response;
      try {
        dotaApiRateLimiter.acquire();
        response = matchesApi.getMatchesByMatchId(match.getId());
      } catch (ApiException e) {
        if (e.getCode() != NOT_FOUND) {
          log.warn("Backfill stopped at match {}: {}", match.getId(), e.getMessage());
          break;
        }
        log.info("Match {} is gone from OpenDota, nothing to backfill", match.getId());
        match.setDetailsMappedAt(LocalDateTime.now());
        matchRepo.save(match);
        completed++;
        continue;
      }
      matchDetailService.replace(response, match);
      matchRepo.save(match);
      completed++;
    }
    if (!pending.isEmpty()) {
      log.info("Match detail backfill: {} of {} matches done", completed, pending.size());
    }
    return completed;
  }
}
