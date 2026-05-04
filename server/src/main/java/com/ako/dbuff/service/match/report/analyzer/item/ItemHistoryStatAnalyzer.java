package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class ItemHistoryStatAnalyzer implements ItemAnalyzer {

  private static final int HISTORY_MONTHS = 12;

  private final ItemRepository itemRepository;

  @Override
  public List<ItemReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<ItemDomain> items,
      MatchReportContext context) {

    Long heroId = playerStat.getHeroId();
    Long matchId = context.getMatch().getId();
    long sinceEpoch = Instant.now().minus(HISTORY_MONTHS * 30L, ChronoUnit.DAYS).getEpochSecond();

    List<Object[]> avgRows =
        itemRepository.findAvgStatsByPlayerAndHero(playerId, heroId, matchId, sinceEpoch);

    Map<Long, Object[]> avgByItemId =
        avgRows.stream()
            .collect(Collectors.toMap(row -> (Long) row[0], Function.identity(), (a, b) -> a));

    Map<Long, ItemDomain> currentByItemId =
        items.stream()
            .collect(Collectors.toMap(ItemDomain::getItemId, Function.identity(), (a, b) -> a));

    List<ItemReport> reports = new ArrayList<>();

    for (var entry : currentByItemId.entrySet()) {
      Long itemId = entry.getKey();
      ItemDomain current = entry.getValue();
      Object[] avg = avgByItemId.get(itemId);

      ItemHistoryStatReport report = new ItemHistoryStatReport();
      report.setPlayerId(playerId);
      report.setItemId(itemId);
      report.setPrettyItemName(current.getItemPrettyName());
      report.setHeroPrettyName(playerStat.getHeroPrettyName());
      report.setHeroId(heroId);
      report.setCurrentDamageDealt(val(current.getDamageDealt()));
      report.setCurrentDamageReceived(val(current.getDamageReceived()));
      report.setCurrentUseCount(val(current.getUseCount()));
      report.setHistoryMonths(HISTORY_MONTHS);

      if (avg != null) {
        report.setAvgDamageDealt(doubleVal(avg[3]));
        report.setAvgDamageReceived(doubleVal(avg[4]));
        report.setAvgUseCount(doubleVal(avg[5]));
        report.setHistoricalMatchCount(((Number) avg[6]).longValue());
      }

      reports.add(report);
    }

    return reports;
  }

  private static long val(Long v) {
    return v != null ? v : 0;
  }

  private static double doubleVal(Object v) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    return 0;
  }
}
