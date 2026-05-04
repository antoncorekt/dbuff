package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class ItemEfficiencyAnalyzer implements ReportAnalyzer {

  private static final int HISTORY_MONTHS = 12;

  private final ItemEfficiencyConfig config;
  private final ItemRepository itemRepository;

  @Override
  public List<Report> analyze(MatchReportContext context) {
    if (config.getGroups() == null || config.getGroups().isEmpty()) {
      return List.of();
    }

    List<Report> results = new ArrayList<>();
    Long matchId = context.getMatch().getId();

    for (Long playerId : context.getFocusPlayerIds()) {
      PlayerMatchStatisticDomain playerStat = context.getStatPerPlayer().get(playerId);
      if (playerStat == null) {
        continue;
      }

      List<ItemDomain> playerItems = context.getItemsByPlayers().getOrDefault(playerId, List.of());
      Set<Long> playerItemIds =
          playerItems.stream().map(ItemDomain::getItemId).collect(Collectors.toSet());

      Map<Long, Long> currentPurchaseTimes =
          playerItems.stream()
              .filter(i -> i.getItemPurchaseTime() != null)
              .collect(
                  Collectors.toMap(
                      ItemDomain::getItemId, ItemDomain::getItemPurchaseTime, (a, b) -> a));

      Map<Long, String> itemIdToName =
          playerItems.stream()
              .collect(
                  Collectors.toMap(
                      ItemDomain::getItemId,
                      i -> i.getItemPrettyName() != null ? i.getItemPrettyName() : i.getItemName(),
                      (a, b) -> a));

      for (ItemEfficiencyConfig.ItemGroup group : config.getGroups()) {
        if (!playerItemIds.containsAll(group.getItemIds())) {
          continue;
        }

        ItemEfficiencyReport report =
            buildReport(playerId, matchId, group, currentPurchaseTimes, itemIdToName);
        results.add(report);
      }
    }

    return results;
  }

  private ItemEfficiencyReport buildReport(
      Long playerId,
      Long matchId,
      ItemEfficiencyConfig.ItemGroup group,
      Map<Long, Long> currentPurchaseTimes,
      Map<Long, String> itemIdToName) {

    List<Long> itemIds = group.getItemIds();
    int requiredCount = itemIds.size();

    List<Object[]> rows =
        itemRepository.findItemRecordsForPlayer(
            playerId,
            itemIds,
            matchId,
            Instant.now().minus(HISTORY_MONTHS * 30L, ChronoUnit.DAYS).getEpochSecond());

    // Group by matchId: matchId -> { itemId -> purchaseTime, win }
    Map<Long, MatchRecord> byMatch = new HashMap<>();
    for (Object[] row : rows) {
      Long mId = (Long) row[0];
      Long itemId = (Long) row[1];
      Long purchaseTime = (Long) row[2];
      Long win = (Long) row[3];

      byMatch
          .computeIfAbsent(mId, k -> new MatchRecord(win))
          .itemPurchaseTimes
          .put(itemId, purchaseTime);
    }

    // Keep only matches where ALL items in the group are present
    int winCount = 0;
    int loseCount = 0;
    Map<Long, List<Long>> purchaseTimesPerItem = new HashMap<>();

    for (var entry : byMatch.entrySet()) {
      MatchRecord record = entry.getValue();
      if (record.itemPurchaseTimes.size() < requiredCount) {
        continue;
      }
      // Verify all required items are present
      if (!record.itemPurchaseTimes.keySet().containsAll(new HashSet<>(itemIds))) {
        continue;
      }

      if (record.win != null && record.win == 1) {
        winCount++;
      } else {
        loseCount++;
      }

      for (Long itemId : itemIds) {
        Long pt = record.itemPurchaseTimes.get(itemId);
        if (pt != null) {
          purchaseTimesPerItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(pt);
        }
      }
    }

    int totalMatches = winCount + loseCount;
    double winRate = totalMatches > 0 ? (double) winCount / totalMatches : 0;

    Map<Long, Double> avgPurchaseTimes = new LinkedHashMap<>();
    for (Long itemId : itemIds) {
      List<Long> times = purchaseTimesPerItem.getOrDefault(itemId, List.of());
      if (!times.isEmpty()) {
        avgPurchaseTimes.put(itemId, times.stream().mapToLong(Long::longValue).average().orElse(0));
      }
    }

    Map<Long, Long> currentTimes = new LinkedHashMap<>();
    for (Long itemId : itemIds) {
      Long t = currentPurchaseTimes.get(itemId);
      if (t != null) {
        currentTimes.put(itemId, t);
      }
    }

    List<String> names =
        itemIds.stream().map(id -> itemIdToName.getOrDefault(id, "Item " + id)).toList();

    ItemEfficiencyReport report = new ItemEfficiencyReport();
    report.setPlayerId(playerId);
    report.setGroupName(group.getName());
    report.setItemIds(itemIds);
    report.setItemNames(names);
    report.setTotalMatches(totalMatches);
    report.setWinCount(winCount);
    report.setLoseCount(loseCount);
    report.setWinRate(winRate);
    report.setAvgPurchaseTimeByItem(avgPurchaseTimes);
    report.setCurrentPurchaseTimeByItem(currentTimes);
    report.setProps(new LinkedHashMap<>());
    report.setHistoryMonths(HISTORY_MONTHS);

    return report;
  }

  private static class MatchRecord {
    Long win;
    Map<Long, Long> itemPurchaseTimes = new HashMap<>();

    MatchRecord(Long win) {
      this.win = win;
    }
  }

  @Override
  public String getAnalyzerName() {
    return "ItemEfficiency";
  }

  @Override
  public int getOrder() {
    return 4;
  }
}
