package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Works out the facts that say why one outlier stretch is what it is, from what is stored. */
final class OutlierExplainer {
  private static final int SECONDS_PER_MINUTE = 60;

  /** What the explanation adds to an outlier row; the columns are null for what is not known. */
  record Explanation(Integer incomeBreakSeconds, String mainIncomeSource, String text) {}

  private final Map<Long, List<ReplayPlayerMinuteDomain>> minutesBySlot;
  private final Map<Long, List<DeathAnalysisDomain>> deathsBySlot;
  private final int totalFights;
  private final ReplayAnalysisProperties properties;

  OutlierExplainer(
      Map<Long, List<ReplayPlayerMinuteDomain>> minutesBySlot,
      Map<Long, List<DeathAnalysisDomain>> deathsBySlot,
      int totalFights,
      ReplayAnalysisProperties properties) {
    this.minutesBySlot = minutesBySlot;
    this.deathsBySlot = deathsBySlot;
    this.totalFights = totalFights;
    this.properties = properties;
  }

  /**
   * @param own his net worth at each minute from 0
   * @param mates his teammates' net worth at each minute from 0
   * @param startMinute first minute of the outlier stretch, inclusive
   * @param endMinute last minute of the stretch, inclusive
   */
  Explanation explain(
      boolean feeder,
      long slot,
      double[] own,
      List<double[]> mates,
      int startMinute,
      int endMinute,
      int fightsJoined,
      double medianFights) {
    OptionalInt breakMinute =
        IncomeBreak.find(
            own,
            mates,
            !feeder,
            properties.getIncomeBreakMinutes(),
            properties.getIncomeBreakShare(),
            startMinute,
            endMinute);
    Optional<IncomeSource.Main> main =
        breakMinute.isEmpty()
            ? Optional.empty()
            : IncomeSource.main(
                minutesBySlot.getOrDefault(slot, List.of()),
                Math.max(breakMinute.getAsInt(), startMinute),
                endMinute + 1);
    List<DeathAnalysisDomain> windowDeaths = windowDeaths(slot, startMinute, endMinute);
    Map<String, Integer> byReason = new HashMap<>();
    windowDeaths.stream()
        .filter(d -> OutlierWording.hasDeathWording(d.getReason()))
        .forEach(d -> byReason.merge(d.getReason(), 1, Integer::sum));
    // The commonest worded reason; a tie goes to the reason that comes first alphabetically.
    Map.Entry<String, Integer> top =
        byReason.entrySet().stream()
            .max(
                Map.Entry.<String, Integer>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .orElse(null);
    String text =
        OutlierWording.sentence(
            new OutlierWording.Facts(
                feeder,
                breakMinute.isPresent() ? breakMinute.getAsInt() : null,
                main.orElse(null),
                fightsJoined,
                totalFights,
                medianFights,
                windowDeaths.size(),
                top == null ? null : top.getKey(),
                top == null ? 0 : top.getValue()));
    return new Explanation(
        breakMinute.isPresent() ? breakMinute.getAsInt() * SECONDS_PER_MINUTE : null,
        main.map(m -> m.source().name()).orElse(null),
        text.isEmpty() ? null : text);
  }

  private List<DeathAnalysisDomain> windowDeaths(long slot, int startMinute, int endMinute) {
    double from = startMinute * (double) SECONDS_PER_MINUTE;
    double to = (endMinute + 1) * (double) SECONDS_PER_MINUTE;
    return deathsBySlot.getOrDefault(slot, List.of()).stream()
        .filter(d -> d.getDeathSeconds() != null && !"REINCARNATED".equals(d.getReason()))
        .filter(d -> d.getDeathSeconds() >= from && d.getDeathSeconds() < to)
        .toList();
  }
}
