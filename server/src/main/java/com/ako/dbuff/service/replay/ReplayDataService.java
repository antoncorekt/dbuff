package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes everything the replay pipeline stored for a match. */
@Service
@RequiredArgsConstructor
public class ReplayDataService {
  private final List<ReplayExtractor> extractors;
  private final ReplayAnalysisService analysis;
  private final ReplayParseRunRepo runRepo;

  @Transactional
  public void deleteByMatchId(long matchId) {
    extractors.forEach(extractor -> extractor.deleteByMatchId(matchId));
    analysis.deleteByMatchId(matchId);
    runRepo.deleteByMatchId(matchId);
  }
}
