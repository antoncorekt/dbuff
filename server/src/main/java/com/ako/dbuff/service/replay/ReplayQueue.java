package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Picks the matches the replay job takes next: due retries first, then the newest new ones. */
@Component
@RequiredArgsConstructor
public class ReplayQueue {
  private final MatchRepo matchRepo;
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;

  public List<MatchDomain> next(int limit) {
    List<MatchDomain> picked = new ArrayList<>();
    List<Long> retryIds =
        runRepo.findDueRetries(properties.getParserVersion(), LocalDateTime.now()).stream()
            .map(ReplayParseRunDomain::getMatchId)
            .limit(limit)
            .toList();
    picked.addAll(matchRepo.findAllById(retryIds));
    int room = limit - picked.size();
    if (room > 0) {
      long recentAfter =
          Instant.now().minus(properties.getMaxAgeDays(), ChronoUnit.DAYS).getEpochSecond();
      picked.addAll(
          matchRepo.findMatchesWithoutReplayRun(
              properties.getParserVersion(), recentAfter, PageRequest.of(0, room)));
    }
    return picked;
  }
}
