package com.ako.dbuff.service.details;

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

  public MatchDomain handle(long matchId) {

    Optional<MatchDomain> matchOpt = matchRepo.findById(matchId);

    if (matchOpt.filter(match -> match.getEndProcess() != null).isPresent()) {

      if (playerGameStatisticRepo.findAllByMatchId(matchId).stream()
          .filter(x -> x.getHasItems() != null)
          .filter(x -> x.getHasAbilities() != null)
          .anyMatch(x -> x.getHasAbilities() && x.getHasItems())) {
        log.info("match {} is already scrapped and has Items and has Abilities", matchId);
        return null;
      } else {
        log.info(
            "match {} found, but items or abilities not found, will try to parse this match again",
            matchId);
      }
    }

    MatchDomain matchDomain =
        matchOpt.orElseGet(() -> matchRepo.save(MatchDomain.builder().id(matchId).build()));

    matchDomain.setStartProcess(LocalDateTime.now());

    try {
      MatchResponse matchResponse = dotaApiMatchDetailsService.fetchMatchDetails(matchId);

      log.info("Successfully fetched match details for {}", matchId);

      basicMatchDetailsMapper.handle(matchResponse, matchDomain);
      matchRepo.flush();

      log.info("Successfully mapped match details for {}", matchId);
    } catch (Exception e) {
      log.error("Failed to fetch match details for {}", matchId, e);
      matchDomain.setDotaApiFailed(true);
      if (e.getCause() instanceof ApiException apiException) {
        if (apiException.getMessage().contains("Not Found")) {
          log.info(
              "Not found match in DotaAPI for {}, trying to scrapp this from dotabuff", matchId);
          Document doc = dotabuffMatchScrapperService.scrap(matchId);
          matchDomain.setEndProcess(LocalDateTime.now());
          return dotabuffMatchMapperService.parse(doc, matchDomain);
        }
      }
    }

    matchDomain.setEndProcess(LocalDateTime.now());
    return matchRepo.save(matchDomain);
  }
}
