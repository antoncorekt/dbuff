package com.ako.dbuff.service.list;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.match.MatchProcessorService;
import java.util.List;
import javax.annotation.PostConstruct;
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

  public void processHistoryMatches(
      Long playerId, int gameMode, AllHistoryService.ScrapperParams scrapperParams) {

    List<MatchDomain> allByEndProcessIsNull = matchRepo.findAllByEndProcessIsNull();
    // Wait for all unprocessed matches to be processed before fetching new ones
    matchProcessorService.process(allByEndProcessIsNull).join();

    List<MatchDomain> allMatches =
        allHistoryService.findAllUserMatches(playerId, gameMode, scrapperParams);

    log.info("Found {} matches for player {}", allMatches.size(), playerId);
  }

  @PostConstruct
  public void init() {
    //   processHistoryMatches(208611215L);
    //    matchProcessorService.process(List.of(MatchDomain.builder()
    //            .id(8446997792L)
    //        .build()));
  }
}
