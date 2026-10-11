package com.ako.dbuff.service.replay.map;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The frozen map layout of the map version a match was played on. */
@Component
@RequiredArgsConstructor
public class MatchMapLayout {
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties processing;
  private final MapLayouts mapLayouts;

  /** Empty when the match has no run, or its map version has not been frozen. */
  public Optional<MapLayout> of(long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, processing.getParserVersion())
        .map(ReplayParseRunDomain::getMapVersion)
        .flatMap(mapLayouts::forVersion);
  }
}
