package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import java.util.List;

/**
 * What the recent-matches lists said of a match that was just found: its length, mode and how each
 * tracked player did.
 */
public record NewMatchSummary(Long duration, Long gameMode, List<PlayerLine> lines) {

  public NewMatchSummary {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }

  public static NewMatchSummary empty() {
    return new NewMatchSummary(null, null, List.of());
  }
}
