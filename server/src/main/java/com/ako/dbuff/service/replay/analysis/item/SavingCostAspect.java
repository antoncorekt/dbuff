package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What saving up for an expensive item cost the hero: the deaths while he held the gold for it. The
 * window runs from the first sample with at least {@code savingThreshold} of the cost in hand, with
 * no bigger purchase since, up to the purchase. A death in it costs {@link DeathCost}; the damage
 * and kill participation per minute in it are shown against the window just before it.
 */
@Component
public class SavingCostAspect implements ItemAspect {
  public static final String NAME = "saving-cost";

  static final String NO_GOLD_TRACK = "NO_GOLD_TRACK";
  static final String NO_MINUTE_GOLD = "NO_MINUTE_GOLD";
  static final String SAVING = "SAVING";

  private static final double SECONDS_PER_MINUTE = 60;
  private static final double MIN_BEFORE_SECONDS = 30;
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(WorthPrices.NAMES);
    names.addAll(Set.of("savingThreshold", "smallPurchase"));
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
    ReplayPlayerTrackDomain track = context.hero().track();
    if (!hasGold(track)) {
      return part(0, Map.of(), List.of(NO_GOLD_TRACK), List.of(), 0);
    }
    Long cost = context.cost();
    if (cost == null) {
      return part(0, Map.of(), List.of(), List.of(), 0);
    }
    double purchase = context.bought();
    double lastBig = lastBigPurchase(context, purchase);
    double wanted = context.entry().parameter("savingThreshold", 0.4) * cost;
    Window window = Window.of(track, lastBig, purchase, wanted);
    if (window == null) {
      return part(0, Map.of(), List.of(), List.of(), 0);
    }
    return withDeaths(context, track, window);
  }

  private AspectPart withDeaths(AspectContext context, ReplayPlayerTrackDomain track, Window w) {
    HeroInputs hero = context.hero();
    DeathCost deathCost = new DeathCost(hero, WorthPrices.of(context));
    double lost = 0;
    double cost = 0;
    double heldMost = 0;
    boolean estimated = false;
    boolean noMinuteGold = false;
    int deaths = 0;
    for (CombatEvent e : hero.events()) {
      if (!isOwnDeath(hero, e) || e.gameSecond() < w.start() || e.gameSecond() >= w.end()) {
        continue;
      }
      DeathCost.Cost c = deathCost.at(e.gameSecond());
      deaths++;
      lost += c.goldLost();
      cost += c.value();
      estimated |= c.estimated();
      noMinuteGold |= c.goldPerMinute() == 0;
      heldMost = Math.max(heldMost, goldBefore(track, e.gameSecond()));
    }
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("savingSeconds", w.end() - w.start());
    figures.put("savingPeakGold", w.peak());
    figures.put("savingDeaths", (double) deaths);
    figures.put("savingGoldHeldAtDeath", heldMost);
    figures.put("savingGoldLost", lost);
    figures.put(DamageAspects.GAIN + NAME, -cost);
    Map<String, Double> row = new LinkedHashMap<>(figures);
    comparison(hero, w, row);
    return part(
        -cost,
        figures,
        flags(estimated, noMinuteGold),
        List.of(new Detail(SAVING, w.start(), null, row)),
        deaths);
  }

  private static List<String> flags(boolean estimated, boolean noMinuteGold) {
    List<String> flags = new ArrayList<>();
    if (estimated) {
      flags.add(SaveAspect.DEATH_COST_ESTIMATED);
    }
    if (noMinuteGold) {
      flags.add(NO_MINUTE_GOLD);
    }
    return flags;
  }

  /** Damage and kill participation per minute in the window and in the equally long one before. */
  private static void comparison(HeroInputs hero, Window w, Map<String, Double> row) {
    double minutes = (w.end() - w.start()) / SECONDS_PER_MINUTE;
    double from = Math.max(0, w.start() - (w.end() - w.start()));
    double beforeMinutes = (w.start() - from) / SECONDS_PER_MINUTE;
    row.put("damagePerMinute", hero.heroDamage(w.start(), w.end()) / minutes);
    row.put("participationPerMinute", participation(hero, w.start(), w.end()) / minutes);
    boolean hasBefore = beforeMinutes * SECONDS_PER_MINUTE >= MIN_BEFORE_SECONDS;
    row.put(
        "damagePerMinuteBefore",
        hasBefore ? hero.heroDamage(from, w.start()) / beforeMinutes : null);
    row.put(
        "participationPerMinuteBefore",
        hasBefore ? participation(hero, from, w.start()) / beforeMinutes : null);
  }

  /** Enemy hero deaths in {@code [from, to)} that the hero killed or assisted. */
  private static long participation(HeroInputs hero, double from, double to) {
    int index = PlayerSlots.index(hero.slot());
    return hero.match().events().stream()
        .filter(e -> e.kind() == CombatEventKind.DEATH && e.targetIsRealHero())
        .filter(e -> e.gameSecond() >= from && e.gameSecond() < to)
        .filter(e -> hero.match().enemies(hero.hero(), e.target()))
        .filter(e -> hero.hero().equals(e.attacker()) || (e.assistMask() >> index & 1) != 0)
        .count();
  }

  private static boolean isOwnDeath(HeroInputs hero, CombatEvent e) {
    return e.kind() == CombatEventKind.DEATH
        && hero.hero().equals(e.target())
        && e.targetIsRealHero();
  }

  /**
   * The latest purchase before it of an item dearer than {@code smallPurchase}, not a part of it.
   */
  private static double lastBigPurchase(AspectContext context, double before) {
    double small = context.entry().parameter("smallPurchase", 500);
    Set<String> parts = new HashSet<>(context.names());
    context.held().forEach(name -> collectComponents(context.kits(), name, parts));
    double last = Double.NEGATIVE_INFINITY;
    for (ReplayStateChangeDomain c : context.hero().changes()) {
      if (c.getGameSeconds() >= before) {
        break;
      }
      if (StateChangeKind.ITEM_ADDED.name().equals(c.getKind())
          && !parts.contains(c.getName())
          && context.kits().cost(HeroInputs.itemKey(c.getName())).orElse(0) > small) {
        last = c.getGameSeconds();
      }
    }
    return last;
  }

  private static void collectComponents(ItemKits kits, String name, Set<String> into) {
    kits.item(HeroInputs.itemKey(name))
        .map(item -> item.getComponents())
        .ifPresent(
            components ->
                components.forEach(
                    key -> {
                      if (into.add(HeroInputs.replayName(key))) {
                        collectComponents(kits, HeroInputs.replayName(key), into);
                      }
                    }));
  }

  /** The gold in the last sample before the second, 0 when there is none. */
  private static double goldBefore(ReplayPlayerTrackDomain track, double second) {
    int i = (int) Math.ceil((second - track.getStartSeconds()) / track.getStepSeconds()) - 1;
    return i >= 0 && i < track.getGold().length ? track.getGold()[i] : 0;
  }

  private static boolean hasGold(ReplayPlayerTrackDomain track) {
    return track != null
        && track.getGold() != null
        && track.getStartSeconds() != null
        && track.getStepSeconds() != null
        && track.getStepSeconds() > 0;
  }

  private static AspectPart part(
      double gain, Map<String, Double> figures, List<String> flags, List<Detail> rows, int count) {
    return new AspectPart(
        gain, true, new LinkedHashMap<>(figures), flags, new ArrayList<>(rows), count, null, null);
  }

  private record Window(double start, double end, double peak) {
    static Window of(
        ReplayPlayerTrackDomain track, double notBefore, double purchase, double wanted) {
      double first = Double.NaN;
      double peak = 0;
      int[] gold = track.getGold();
      for (int i = 0; i < gold.length; i++) {
        double t = track.getStartSeconds() + (double) i * track.getStepSeconds();
        if (t >= purchase) {
          break;
        }
        if (t < notBefore || (gold[i] < wanted && Double.isNaN(first))) {
          continue;
        }
        if (Double.isNaN(first)) {
          first = t;
        }
        peak = Math.max(peak, gold[i]);
      }
      return Double.isNaN(first) ? null : new Window(first, purchase, peak);
    }
  }
}
