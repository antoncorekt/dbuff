package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.item.FarmWindows.Window;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Items bought to farm faster (Battle Fury, Maelstrom, Mjollnir...): creep kills, creep gold and
 * camps cleared per minute in the window before the hero's first item and the window after it, and
 * what the difference pays against the item's cost. It is a before and after comparison, not a
 * proof: the game around the hero changes with time too.
 */
@Component
public class FarmRateAspect implements ItemAspect {
  public static final String NAME = "farm-rate";

  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names =
        new java.util.HashSet<>(
            Set.of("windowMinutes", "minBeforeSeconds", "minAfterSeconds", "campGapSeconds"));
    names.addAll(WorthPrices.NAMES);
    return Set.copyOf(names);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    FarmWindows windows = FarmWindows.of(context);
    double gap = context.entry().parameter("campGapSeconds", 30);
    Map<String, Double> figures = new LinkedHashMap<>();
    double gain = 0;
    Double perMinute = null;
    if (windows.comparable()) {
      double expected = expectedGoldPerMinute(context, windows);
      double net = windows.after().goldPerMinute() - expected;
      gain = net * windows.ownedMinutes();
      perMinute = net;
      figures.put("netGoldPerMinute", net);
      figures.put("creepGain", gain);
      figures.put(DamageAspects.GAIN + NAME, gain);
    }
    figures.put("minutesOwned", windows.ownedMinutes());
    return new AspectPart(
        gain,
        windows.comparable(),
        figures,
        windows.flags(),
        List.of(detail(windows.before(), gap), detail(windows.after(), gap)),
        windows.after().kills().size(),
        null,
        perMinute);
  }

  /**
   * What the hero's own trend of creep gold before the purchase expects over the after window; the
   * before window's rate when the trend has too few minutes behind it.
   */
  private static double expectedGoldPerMinute(AspectContext context, FarmWindows windows) {
    int from = MinuteSeries.minuteOf(windows.bought());
    int to =
        Math.max(from + 1, (int) Math.ceil(windows.after().to() / FarmWindows.SECONDS_PER_MINUTE));
    TrendBaseline trend =
        TrendBaseline.fit(
            goldPerMinute(context.hero().creepKills(), from),
            from,
            (int) WorthPrices.of(context).trendMinutes());
    return trend.trusted() ? trend.mean(from, to) : windows.before().goldPerMinute();
  }

  private static double[] goldPerMinute(List<CreepKillDomain> kills, int minutes) {
    double[] series = new double[Math.max(minutes, 0)];
    for (CreepKillDomain k : kills) {
      if (k.getSeconds() != null && !Boolean.TRUE.equals(k.getDenied()) && k.getSeconds() >= 0) {
        int minute = MinuteSeries.minuteOf(k.getSeconds());
        if (minute < series.length) {
          series[minute] += k.getGold() == null ? 0 : k.getGold();
        }
      }
    }
    return series;
  }

  private static Detail detail(Window w, double campGapSeconds) {
    Map<String, Double> figures = new LinkedHashMap<>();
    boolean measured = w.seconds() > 0;
    double minutes = w.minutes();
    figures.put("minutes", measured ? minutes : null);
    figures.put("kills", (double) w.kills().size());
    figures.put("killsPerMinute", measured ? w.kills().size() / minutes : null);
    figures.put("goldPerMinute", measured ? w.gold() / minutes : null);
    figures.put("campsPerMinute", measured ? camps(w, campGapSeconds) / minutes : null);
    return new Detail(w.label(), w.from(), null, figures);
  }

  /** Camps cleared: neutral kills in one camp that follow each other within the gap are one. */
  private static int camps(Window w, double gapSeconds) {
    Map<Integer, Double> lastKill = new HashMap<>();
    int camps = 0;
    for (CreepKillDomain k : w.kills()) {
      CreepClass creep = CreepClass.of(k.getCreepName());
      if (creep == null || !creep.isNeutral() || k.getCamp() == null) {
        continue;
      }
      Double last = lastKill.put(k.getCamp(), k.getSeconds());
      if (last == null || k.getSeconds() - last > gapSeconds) {
        camps++;
      }
    }
    return camps;
  }
}
