package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the players whose share of the team's net worth does not fit how much they took part: a big
 * share with few fights joined, and a small share with many deaths.
 */
@Slf4j
@Order(65)
@Component
@RequiredArgsConstructor
public class OutlierAnalyzer implements ReplayAnalyzer {
  private static final long FIRST_DIRE_SLOT = 128;
  private static final Set<String> JOINED = Set.of("ACTIVE", "PRESENT_PASSIVE");
  private static final int TEAM_SIZE = 5;
  private static final int MIN_FEEDER_DEATHS = 5;
  private static final int FEEDER_DEATH_FACTOR = 2;

  private final PlayerTimelineRepo timelineRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final DeathAnalysisRepo deathRepo;
  private final PlayerOutlierRepo outlierRepo;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "outliers";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Map<Long, double[]> worth = worthBySlot(timelineRepo.findByMatchId(matchId));
    if (worth == null) {
      log.info("Match {} has no complete net worth timeline, so no outliers", matchId);
      return;
    }
    Map<Long, Integer> joined = new HashMap<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      if (JOINED.contains(p.getPresence())) {
        joined.merge(p.getPlayerSlot(), 1, Integer::sum);
      }
    }
    Map<Long, Integer> deaths = new HashMap<>();
    for (DeathAnalysisDomain d : deathRepo.findByMatchId(matchId)) {
      if (!"REINCARNATED".equals(d.getReason())) {
        deaths.merge(d.getPlayerSlot(), 1, Integer::sum);
      }
    }
    List<PlayerOutlierDomain> rows = new ArrayList<>();
    for (boolean dire : new boolean[] {false, true}) {
      rows.addAll(team(matchId, worth, joined, deaths, dire));
    }
    outlierRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    outlierRepo.deleteByMatchId(matchId);
  }

  private List<PlayerOutlierDomain> team(
      long matchId,
      Map<Long, double[]> worth,
      Map<Long, Integer> joined,
      Map<Long, Integer> deaths,
      boolean dire) {
    List<Long> slots = worth.keySet().stream().filter(s -> (s >= FIRST_DIRE_SLOT) == dire).toList();
    if (slots.size() != TEAM_SIZE) {
      return List.of();
    }
    int minutes = worth.get(slots.getFirst()).length;
    double[] total = new double[minutes];
    slots.forEach(s -> add(total, worth.get(s)));
    double medianJoined = median(slots.stream().map(s -> joined.getOrDefault(s, 0)).toList());
    double medianDeaths = median(slots.stream().map(s -> deaths.getOrDefault(s, 0)).toList());
    List<PlayerOutlierDomain> rows = new ArrayList<>();
    for (long slot : slots) {
      double[] share = shares(worth.get(slot), total);
      int fights = joined.getOrDefault(slot, 0);
      int died = deaths.getOrDefault(slot, 0);
      int rank = 1 + (int) slots.stream().filter(o -> joined.getOrDefault(o, 0) > fights).count();
      if (fights < medianJoined) {
        ShareRuns.of(
                share,
                s -> s > properties.getOutlierSharePercent() / 100.0,
                Math.max(1, properties.getOutlierMinutes()),
                true)
            .forEach(r -> rows.add(row(matchId, slot, "NW_OUTLIER", r, rank, fights, died)));
      }
      if (died >= MIN_FEEDER_DEATHS && died > FEEDER_DEATH_FACTOR * medianDeaths) {
        ShareRuns.of(
                share,
                s -> s < properties.getFeederSharePercent() / 100.0,
                Math.max(1, properties.getFeederMinutes()),
                false)
            .forEach(r -> rows.add(row(matchId, slot, "FEEDER", r, rank, fights, died)));
      }
    }
    return rows;
  }

  private PlayerOutlierDomain row(
      long matchId, long slot, String kind, ShareRuns.Run run, int rank, int fights, int deaths) {
    return PlayerOutlierDomain.builder()
        .matchId(matchId)
        .playerSlot(slot)
        .kind(kind)
        .startSeconds(run.startMinute() * 60)
        .endSeconds(run.endMinute() * 60)
        .sharePercent(Math.round(run.extreme() * 1000) / 10.0)
        .participationRank(rank)
        .fightsJoined(fights)
        .deaths(deaths)
        .analysisVersion(version())
        .build();
  }

  private static void add(double[] total, double[] part) {
    for (int i = 0; i < total.length; i++) {
      total[i] += part[i];
    }
  }

  private static double[] shares(double[] part, double[] total) {
    double[] share = new double[total.length];
    for (int i = 0; i < share.length; i++) {
      share[i] = total[i] > 0 ? part[i] / total[i] : 0;
    }
    return share;
  }

  private static double median(List<Integer> values) {
    int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
    int n = sorted.length;
    return n == 0 ? 0 : n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
  }

  /** Net worth by minute for each slot; null unless every player has every minute from 0. */
  private static Map<Long, double[]> worthBySlot(List<PlayerTimelineDomain> rows) {
    Map<Long, TreeMap<Integer, Long>> bySlot = new TreeMap<>();
    for (PlayerTimelineDomain row : rows) {
      if (row.getNetworth() == null || row.getGameMinute() == null || row.getPlayerSlot() == null) {
        return null;
      }
      bySlot
          .computeIfAbsent(row.getPlayerSlot(), s -> new TreeMap<>())
          .put(row.getGameMinute(), row.getNetworth());
    }
    if (bySlot.isEmpty()) {
      return null;
    }
    int minutes = bySlot.values().iterator().next().size();
    Map<Long, double[]> result = new TreeMap<>();
    for (Map.Entry<Long, TreeMap<Integer, Long>> e : bySlot.entrySet()) {
      TreeMap<Integer, Long> series = e.getValue();
      if (series.size() != minutes || series.firstKey() != 0 || series.lastKey() != minutes - 1) {
        return null;
      }
      result.put(
          e.getKey(),
          Arrays.stream(series.values().toArray(new Long[0]))
              .mapToDouble(Long::doubleValue)
              .toArray());
    }
    return result;
  }
}
