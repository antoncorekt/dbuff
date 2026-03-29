package com.ako.dbuff.service.list;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.ScrapperApiService;
import com.ako.dbuff.service.match.MatchProcessorService;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class AllHistoryMatchFetcherService {

  private final AllHistoryService allHistoryService;
  private final MatchProcessorService matchProcessorService;
  private final MatchRepo matchRepo;
  private final ScrapperApiService scrapperApiService;

  public void processHistoryMatches(
      Long playerId, Set<Long> gameMode, AllHistoryService.ScrapperParams scrapperParams) {

    if (!scrapperApiService.isEnabled()) {
      throw new ScrapperApiService.ScrapperDisabledException(
          "Cannot fetch match history: scrapper is disabled. "
              + "Use the DotaAPI-based /api/v1/matches endpoint instead.");
    }

    List<MatchDomain> allByEndProcessIsNull = matchRepo.findAllByEndProcessIsNull();
    // Wait for all unprocessed matches to be processed before fetching new ones
    matchProcessorService.process(allByEndProcessIsNull).join();

    List<MatchDomain> allMatches =
        allHistoryService.findAllUserMatches(playerId, gameMode, scrapperParams);

    log.info("Found {} matches for player {}", allMatches.size(), playerId);
  }
}
