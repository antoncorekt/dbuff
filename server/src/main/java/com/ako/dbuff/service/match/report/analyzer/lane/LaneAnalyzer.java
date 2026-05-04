package com.ako.dbuff.service.match.report.analyzer.lane;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LaneAnalyzer implements ReportAnalyzer {

  private static final long LANE_PHASE_END = 600;
  private static final long EARLY_ITEM_CUTOFF = 1200;

  @Override
  public List<Report> analyze(MatchReportContext context) {
    List<PlayerMatchStatisticDomain> allPlayers = context.getPlayerStatistics();
    Set<Long> focusPlayerIds = context.getFocusPlayerIds();
    List<KillLogDomain> killLogs = context.getKillLogs();
    List<ItemDomain> items = context.getItems();

    Map<Long, Integer> laneKillsBySlot = new HashMap<>();
    Map<Long, Integer> laneDeathsBySlot = new HashMap<>();
    for (KillLogDomain kill : killLogs) {
      if (kill.getTime() != null && kill.getTime() <= LANE_PHASE_END) {
        laneKillsBySlot.merge(kill.getPlayerSlot(), 1, Integer::sum);
        if (kill.getKilledPlayerSlot() != null) {
          laneDeathsBySlot.merge(kill.getKilledPlayerSlot(), 1, Integer::sum);
        }
      }
    }

    Map<Integer, List<PlayerMatchStatisticDomain>> byLane = new HashMap<>();
    for (PlayerMatchStatisticDomain p : allPlayers) {
      int lane = resolveLane(p);
      if (lane >= 1 && lane <= 3) {
        byLane.computeIfAbsent(lane, k -> new ArrayList<>()).add(p);
      }
    }

    if (byLane.isEmpty()) {
      log.info("No lane data available for match {}", context.getMatch().getId());
      return List.of();
    }

    List<Report> reports = new ArrayList<>();

    Map<Integer, List<PlayerMatchStatisticDomain>> matchups = buildMatchups(byLane);

    for (var entry : matchups.entrySet()) {
      int matchupId = entry.getKey();
      List<PlayerMatchStatisticDomain> players = entry.getValue();

      List<LaneMatchupReport.LanePlayerSummary> radiant = new ArrayList<>();
      List<LaneMatchupReport.LanePlayerSummary> dire = new ArrayList<>();

      for (PlayerMatchStatisticDomain p : players) {
        LaneMatchupReport.LanePlayerSummary summary =
            buildPlayerSummary(p, focusPlayerIds, laneKillsBySlot, laneDeathsBySlot, items);

        if (p.getIsRadiant() != null && p.getIsRadiant()) {
          radiant.add(summary);
        } else {
          dire.add(summary);
        }
      }

      long radiantGold10 = radiant.stream().mapToLong(s -> s.getGoldAt10()).sum();
      long direGold10 = dire.stream().mapToLong(s -> s.getGoldAt10()).sum();
      long radiantXp10 = radiant.stream().mapToLong(s -> s.getXpAt10()).sum();
      long direXp10 = dire.stream().mapToLong(s -> s.getXpAt10()).sum();

      String outcome = determineLaneOutcome(radiantGold10 + radiantXp10, direGold10 + direXp10);

      LaneMatchupReport report = new LaneMatchupReport();
      report.setLaneId(matchupId);
      report.setLaneName(laneMatchupName(matchupId));
      report.setRadiantPlayers(radiant);
      report.setDirePlayers(dire);
      report.setRadiantGoldAt10(radiantGold10);
      report.setDireGoldAt10(direGold10);
      report.setRadiantXpAt10(radiantXp10);
      report.setDireXpAt10(direXp10);
      report.setLaneOutcome(outcome);

      reports.add(report);
    }

    return reports;
  }

  private Map<Integer, List<PlayerMatchStatisticDomain>> buildMatchups(
      Map<Integer, List<PlayerMatchStatisticDomain>> byLane) {

    Map<Integer, List<PlayerMatchStatisticDomain>> matchups = new HashMap<>();

    // Bottom lane: Radiant Safe (1) vs Dire Off (3)
    List<PlayerMatchStatisticDomain> bottom = new ArrayList<>();
    addPlayersFromLane(bottom, byLane.getOrDefault(1, List.of()), true);
    addPlayersFromLane(bottom, byLane.getOrDefault(3, List.of()), false);
    if (!bottom.isEmpty()) {
      matchups.put(1, bottom);
    }

    // Mid lane: both sides lane 2
    List<PlayerMatchStatisticDomain> mid = byLane.getOrDefault(2, List.of());
    if (!mid.isEmpty()) {
      matchups.put(2, mid);
    }

    // Top lane: Radiant Off (3) vs Dire Safe (1)
    List<PlayerMatchStatisticDomain> top = new ArrayList<>();
    addPlayersFromLane(top, byLane.getOrDefault(3, List.of()), true);
    addPlayersFromLane(top, byLane.getOrDefault(1, List.of()), false);
    if (!top.isEmpty()) {
      matchups.put(3, top);
    }

    return matchups;
  }

  private void addPlayersFromLane(
      List<PlayerMatchStatisticDomain> target,
      List<PlayerMatchStatisticDomain> source,
      boolean radiant) {
    for (PlayerMatchStatisticDomain p : source) {
      boolean isRad = p.getIsRadiant() != null && p.getIsRadiant();
      if (isRad == radiant) {
        target.add(p);
      }
    }
  }

  private LaneMatchupReport.LanePlayerSummary buildPlayerSummary(
      PlayerMatchStatisticDomain p,
      Set<Long> focusPlayerIds,
      Map<Long, Integer> laneKillsBySlot,
      Map<Long, Integer> laneDeathsBySlot,
      List<ItemDomain> allItems) {

    List<ItemDomain> earlyItems =
        allItems.stream()
            .filter(i -> p.getPlayerSlot().equals(i.getPlayerSlot()))
            .filter(
                i ->
                    i.getItemPurchaseTime() != null && i.getItemPurchaseTime() <= EARLY_ITEM_CUTOFF)
            .sorted(Comparator.comparingLong(ItemDomain::getItemPurchaseTime))
            .toList();

    List<LaneMatchupReport.ItemEvent> itemEvents =
        earlyItems.stream()
            .map(
                i ->
                    new LaneMatchupReport.ItemEvent(i.getItemPrettyName(), i.getItemPurchaseTime()))
            .toList();

    Long effPct = p.getLaneEfficiencyPct() != null ? p.getLaneEfficiencyPct().longValue() : null;

    LaneMatchupReport.LanePlayerSummary summary = new LaneMatchupReport.LanePlayerSummary();
    summary.setPlayerId(p.getPlayerId());
    summary.setPlayerSlot(p.getPlayerSlot());
    summary.setHeroPrettyName(p.getHeroPrettyName());
    summary.setFocusPlayer(focusPlayerIds.contains(p.getPlayerId()));
    summary.setLaneEfficiencyPct(effPct);
    summary.setDenies(val(p.getDenies()));
    summary.setLastHitsAt5(val(p.getLastHitsAt5Min()));
    summary.setLastHitsAt10(val(p.getLastHitsAt10Min()));
    summary.setLastHitsAt15(val(p.getLastHitsAt15Min()));
    summary.setLastHitsAt20(val(p.getLastHitsAt20Min()));
    summary.setGoldAt5(val(p.getGoldAt5Min()));
    summary.setGoldAt10(val(p.getGoldAt10Min()));
    summary.setGoldAt15(val(p.getGoldAt15Min()));
    summary.setGoldAt20(val(p.getGoldAt20Min()));
    summary.setXpAt5(val(p.getXpAt5Min()));
    summary.setXpAt10(val(p.getXpAt10Min()));
    summary.setXpAt15(val(p.getXpAt15Min()));
    summary.setXpAt20(val(p.getXpAt20Min()));
    summary.setLaneKills(laneKillsBySlot.getOrDefault(p.getPlayerSlot(), 0));
    summary.setLaneDeaths(laneDeathsBySlot.getOrDefault(p.getPlayerSlot(), 0));
    summary.setEarlyItems(itemEvents);

    return summary;
  }

  private String determineLaneOutcome(long radiantTotal, long direTotal) {
    if (radiantTotal == 0 && direTotal == 0) {
      return "Unknown";
    }
    long max = Math.max(radiantTotal, direTotal);
    double diff = (double) Math.abs(radiantTotal - direTotal) / max;
    if (diff < 0.10) {
      return "Even";
    }
    return radiantTotal > direTotal ? "Radiant Won" : "Dire Won";
  }

  private static String laneMatchupName(int matchupId) {
    return switch (matchupId) {
      case 1 -> "Bottom (Radiant Safe vs Dire Off)";
      case 2 -> "Mid";
      case 3 -> "Top (Radiant Off vs Dire Safe)";
      default -> "Lane " + matchupId;
    };
  }

  private static int resolveLane(PlayerMatchStatisticDomain p) {
    if (p.getLaneRole() != null && p.getLaneRole() >= 1 && p.getLaneRole() <= 3) {
      return p.getLaneRole().intValue();
    }
    if (p.getLane() != null && p.getLane() >= 1 && p.getLane() <= 3) {
      return p.getLane().intValue();
    }
    return 0;
  }

  private static long val(Long v) {
    return v != null ? v : 0;
  }

  @Override
  public String getAnalyzerName() {
    return "LaneAnalysis";
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
