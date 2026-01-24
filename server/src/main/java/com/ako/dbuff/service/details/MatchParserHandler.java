package com.ako.dbuff.service.details;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.match.DotaApiMatchDetailsService;
import com.ako.dbuff.service.match.DotabuffMatchScrapperService;
import com.ako.dbuff.service.match.mapper.DotabuffMatchMapperService;
import com.ako.dbuff.service.match.mapper.MatchInfoMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MatchParserHandler {

  private final DotaApiMatchDetailsService dotaApiMatchDetailsService;
  private final MatchRepo matchRepo;
  private final MatchInfoMapper basicMatchDetailsMapper;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final DotabuffMatchScrapperService dotabuffMatchScrapperService;
  private final DotabuffMatchMapperService dotabuffMatchMapperService;

  /**
   * Handles the processing of a match by its ID.
   * Uses ScopedValue context for logging - matchId should already be in scope
   * from the calling MatchProcessorService.
   *
   * @param matchId the match ID to process
   * @return the processed MatchDomain, or null if already processed
   */
  public MatchDomain handle(long matchId) {
    String ctx = ProcessContext.getContextString();

    Optional<MatchDomain> matchOpt = matchRepo.findById(matchId);

    if (matchOpt.filter(match -> match.getEndProcess() != null).isPresent()) {
      if (isMatchFullyProcessed(matchId)) {
        log.info("{} Match {} is already scrapped and has Items and has Abilities", ctx, matchId);
        return null;
      } else {
        log.info("{} Match {} found, but items or abilities not found, will try to parse again",
            ctx, matchId);
      }
    }

    MatchDomain matchDomain = matchOpt.orElseGet(() -> createNewMatch(matchId));
    matchDomain.setStartProcess(LocalDateTime.now());

    try {
      processMatchFromDotaApi(matchId, matchDomain, ctx);
    } catch (Exception e) {
      handleDotaApiError(matchId, matchDomain, e, ctx);
    }

    matchDomain.setEndProcess(LocalDateTime.now());
    return matchRepo.save(matchDomain);
  }

  private boolean isMatchFullyProcessed(long matchId) {
    return playerGameStatisticRepo.findAllByMatchId(matchId).stream()
        .filter(x -> x.getHasItems() != null)
        .filter(x -> x.getHasAbilities() != null)
        .anyMatch(x -> x.getHasAbilities() && x.getHasItems());
  }

  private MatchDomain createNewMatch(long matchId) {
    return matchRepo.save(MatchDomain.builder().id(matchId).build());
  }

  private void processMatchFromDotaApi(long matchId, MatchDomain matchDomain, String ctx) {
    MatchResponse matchResponse = dotaApiMatchDetailsService.fetchMatchDetails(matchId);
    log.info("{} Successfully fetched match details for {}", ctx, matchId);

    basicMatchDetailsMapper.handle(matchResponse, matchDomain);
    matchRepo.flush();

    log.info("{} Successfully mapped match details for {}", ctx, matchId);
  }

  private void handleDotaApiError(long matchId, MatchDomain matchDomain, Exception e, String ctx) {
    log.error("{} Failed to fetch match details for {}", ctx, matchId, e);
    matchDomain.setDotaApiFailed(true);
    
    if (e.getCause() instanceof ApiException apiException) {
      if (apiException.getMessage().contains("Not Found")) {
        log.info("{} Not found match in DotaAPI for {}, trying to scrape from dotabuff", 
            ctx, matchId);
        Document doc = dotabuffMatchScrapperService.scrap(matchId);
        matchDomain.setEndProcess(LocalDateTime.now());
        dotabuffMatchMapperService.parse(doc, matchDomain);
      }
    }
  }
}
