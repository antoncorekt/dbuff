package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.constant.data.PatchConstant;
import com.ako.dbuff.service.details.DotabuffBuildDetailsParser;
import com.ako.dbuff.service.details.ScrapperService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class BasicMatchDetailsMapper implements MatchInfoMapper {

  private final ConstantsManagers constantsManagers;
  private final PlayersMapper playersMapper;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final MatchRepo matchRepo;
  private final ScrapperService dotaBuffMatchDetailsScrapper;
  private final DotabuffBuildDetailsParser dotabuffBuildDetailsParser;

  private final Cache<Long, Document> scrappedDotabufPage = Caffeine.newBuilder().build();

  /**
   * Handles mapping of match response to domain object.
   * Uses ScopedValue context for logging - matchId should already be in scope.
   *
   * @param matchResponse the API response
   * @param matchDomain the domain object to populate
   */
  @Override
  public void handle(MatchResponse matchResponse, MatchDomain matchDomain) {
    String ctx = ProcessContext.getContextString();
    
    mapGameMode(matchResponse, matchDomain);
    mapTimeInfo(matchResponse, matchDomain);
    mapPatchInfo(matchResponse, matchDomain);
    mapScoreInfo(matchResponse, matchDomain);

    log.debug("{} Processing {} players for match {}", 
        ctx, 
        CollectionUtils.size(matchResponse.getPlayers()), 
        matchDomain.getId());

    CollectionUtils.emptyIfNull(matchResponse.getPlayers())
        .forEach(player -> playersMapper.handle(matchDomain, player));

    matchRepo.save(matchDomain);

    checkAndScrapeAdditionalData(matchDomain);
  }

  private void mapGameMode(MatchResponse matchResponse, MatchDomain matchDomain) {
    Long gameModeId = matchResponse.getGameMode();
    Map<String, MatchTypeConstant> gameModeConstants = constantsManagers.getMatchTypeConstantMap();
    MatchTypeConstant matchType = gameModeConstants.get(String.valueOf(gameModeId));
    String gameModeName = matchType.getName();
    matchDomain.setGameModeName(gameModeName);
  }

  private void mapTimeInfo(MatchResponse matchResponse, MatchDomain matchDomain) {
    matchDomain.setStartTime(matchResponse.getStartTime());
    long startTimeMillis = matchResponse.getStartTime() * 1000;
    matchDomain.setStartTimeMillis(startTimeMillis);
    
    LocalDate localDate = Instant.ofEpochMilli(startTimeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate();
    matchDomain.setStartLocalDate(localDate);
    matchDomain.setStartMonth(localDate.getMonth().getValue());
    matchDomain.setStartYear(localDate.getYear());
    matchDomain.setDuration(matchResponse.getDuration());
    matchDomain.setFirstBloodTime(matchResponse.getFirstBloodTime());
  }

  private void mapPatchInfo(MatchResponse matchResponse, MatchDomain matchDomain) {
    Map<String, PatchConstant> patchConstantMap = constantsManagers.getPatchConstantMap();
    matchDomain.setPatch(matchResponse.getPatch());
    PatchConstant patchConstant = patchConstantMap.get(String.valueOf(matchResponse.getPatch()));
    if (patchConstant != null) {
      matchDomain.setPatchStr(patchConstant.getName());
    }
  }

  private void mapScoreInfo(MatchResponse matchResponse, MatchDomain matchDomain) {
    Boolean radiantWin = matchResponse.getRadiantWin();
    matchDomain.setRadiantWin(radiantWin);
    matchDomain.setRadiantScore(matchResponse.getRadiantScore());
    matchDomain.setDireScore(matchResponse.getDireScore());
  }

  private void checkAndScrapeAdditionalData(MatchDomain matchDomain) {
    String ctx = ProcessContext.getContextString();
    
    playerGameStatisticRepo.findAllByMatchId(matchDomain.getId()).stream()
        .findAny()
        .ifPresent(playerGameStatistic -> {
          boolean needsItems = playerGameStatistic.getHasItems() == null || !playerGameStatistic.getHasItems();
          boolean needsAbilities = playerGameStatistic.getHasAbilities() == null || !playerGameStatistic.getHasAbilities();
          
          if (needsItems || needsAbilities) {
            log.info("{} For match {} items or abilities not found, trying to scrape from dotabuff",
                ctx, matchDomain.getId());
            Document scrappedPage = scrappedDotabufPage.get(
                matchDomain.getId(), 
                k -> dotaBuffMatchDetailsScrapper.scrap(matchDomain));
            dotabuffBuildDetailsParser.parse(scrappedPage, matchDomain);
          }
        });
  }
}
