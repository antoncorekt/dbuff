package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class ChosenItemAnalyzer implements ItemAnalyzer {

  private static final int HISTORY_MONTHS = 12;

  private final ItemRepository itemRepository;

  @Override
  public List<ItemReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<ItemDomain> items,
      MatchReportContext context) {

    List<ItemReport> reports = new ArrayList<>();
    Long heroId = playerStat.getHeroId();
    Long matchId = context.getMatch().getId();
    long sinceEpoch = Instant.now().minus(HISTORY_MONTHS * 30L, ChronoUnit.DAYS).getEpochSecond();

    for (ItemDomain item : items) {
      ChosenItemPlayerReport report = new ChosenItemPlayerReport();
      report.setPlayerId(playerId);
      report.setItemId(item.getItemId());
      report.setPrettyItemName(item.getItemPrettyName());
      report.setHeroPrettyName(playerStat.getHeroPrettyName());
      report.setHeroId(heroId);
      report.setNeutral(item.isNeutral());
      report.setPurchaseTime(item.getItemPurchaseTime());
      report.setHistoryMonths(HISTORY_MONTHS);

      List<Object[]> winLossRows =
          itemRepository.countWinLossByPlayerHeroItem(
              playerId, heroId, item.getItemId(), matchId, sinceEpoch);

      int winCount = 0;
      int loseCount = 0;

      for (Object[] row : winLossRows) {
        Long win = (Long) row[0];
        Long count = (Long) row[1];
        if (win != null && win == 1) {
          winCount = count.intValue();
        } else {
          loseCount = count.intValue();
        }
      }

      int totalMatches = winCount + loseCount;
      double winRate = totalMatches > 0 ? (double) winCount / totalMatches : 0;

      report.setTotalMatches(totalMatches);
      report.setWinCount(winCount);
      report.setLoseCount(loseCount);
      report.setWinRate(winRate);

      reports.add(report);
    }

    return reports;
  }
}
