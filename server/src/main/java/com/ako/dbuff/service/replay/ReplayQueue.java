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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        runRepo
            .findDueRetries(
                properties.getParserVersion(),
                LocalDateTime.now(),
                properties.getMaxAttempts(),
                PageRequest.of(0, limit))
            .stream()
            .map(ReplayParseRunDomain::getMatchId)
            .toList();
    Map<Long, MatchDomain> byId =
        matchRepo.findAllById(retryIds).stream()
            .collect(Collectors.toMap(MatchDomain::getId, Function.identity()));
    retryIds.stream().map(byId::get).filter(Objects::nonNull).forEach(picked::add);
    int room = limit - picked.size();
    if (room > 0) {
      long recentAfter =
          Instant.now().minus(properties.getMaxAgeDays(), ChronoUnit.DAYS).getEpochSecond();
      picked.addAll(
          matchRepo.findMatchesWithoutReplayRun(
              properties.getParserVersion(),
              recentAfter,
              properties.getSupportedModes(),
              PageRequest.of(0, room)));
    }
    return picked;
  }
}
