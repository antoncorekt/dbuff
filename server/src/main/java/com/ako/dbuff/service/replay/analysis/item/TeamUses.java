package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The uses of an item that restores its allies, with who each reached and what they got: the facts
 * {@link TeamRestoreAspect} and {@link SaveAspect} judge. An ally was reached when the log shows
 * the item healing him at the use or the track puts him alive within the radius at the sample
 * nearest the use. Health is what the log says; mana is estimated from the item's constants and
 * capped by the mana the ally was missing.
 */
record TeamUses(List<Use> uses) {
  static final Set<String> PARAMETERS =
      Stream.concat(
              SaveJudge.PARAMETERS.stream(), Stream.of("radius", "manaPerUse", "healWindowSeconds"))
          .collect(Collectors.toUnmodifiableSet());

  private static final List<String> RADIUS_ATTRIBUTES =
      List.of("heal_radius", "replenish_radius", "charge_radius", "aura_radius");
  private static final List<String> MANA_ATTRIBUTES = List.of("replenish_mana", "replenish_amount");
  private static final String MANA_PER_CHARGE = "mana_restore_per_charge";
  private static final double DEFAULT_RADIUS = 1200;
  private static final int ALIVE_FLAG = 1;

  /**
   * One ally the use reached.
   *
   * @param hp the health the log shows the item gave him
   * @param mana the mana estimated, capped by what he was missing when the track tells it
   * @param verdict whether the use saved him
   */
  record Reach(HeroInputs ally, double hp, double mana, SaveJudge.Verdict verdict) {}

  record Use(double second, List<Reach> reached) {
    double hp() {
      return reached.stream().mapToDouble(Reach::hp).sum();
    }

    double mana() {
      return reached.stream().mapToDouble(Reach::mana).sum();
    }

    long saves() {
      return reached.stream().filter(r -> r.verdict().saved()).count();
    }

    long diedSoon() {
      return reached.stream().filter(r -> !r.verdict().survived()).count();
    }
  }

  static TeamUses of(AspectContext context) {
    HeroInputs hero = context.hero();
    List<HeroInputs> allies = hero.match().allies(hero);
    Map<Long, SaveJudge> judges = new HashMap<>();
    allies.forEach(a -> judges.put(a.slot(), new SaveJudge(a, context)));
    List<CombatEvent> heals =
        hero.match().events().stream()
            .filter(e -> e.kind() == CombatEventKind.HEAL && hero.hero().equals(e.attacker()))
            .toList();
    List<Use> uses = new ArrayList<>();
    for (CombatEvent use : hero.uses(context.names())) {
      uses.add(assess(context, allies, judges, heals, use));
    }
    return new TeamUses(uses);
  }

  private static Use assess(
      AspectContext context,
      List<HeroInputs> allies,
      Map<Long, SaveJudge> judges,
      List<CombatEvent> heals,
      CombatEvent use) {
    double t = use.gameSecond();
    String key = HeroInputs.itemKey(use.inflictor());
    double window = context.price("healWindowSeconds", 2.0);
    double radius = radius(context, key);
    OptionalInt position = nearest(context.hero().track(), t);
    double manaPerUse = manaPerUse(context, key, t);
    List<Reach> reached = new ArrayList<>();
    for (HeroInputs ally : allies) {
      double hp =
          heals.stream()
              .filter(h -> use.inflictor().equals(h.inflictor()) && ally.hero().equals(h.target()))
              .filter(h -> Math.abs(h.gameSecond() - t) <= window)
              .mapToDouble(CombatEvent::value)
              .sum();
      if (hp > 0 || within(context.hero().track(), position, ally.track(), t, radius)) {
        SaveJudge judge = judges.get(ally.slot());
        HeroHealth.Mana missing = judge.health().mana(t);
        double mana = missing == null ? manaPerUse : Math.min(manaPerUse, missing.missing());
        reached.add(new Reach(ally, hp, mana, judge.judge(t)));
      }
    }
    return new Use(t, reached);
  }

  private static double radius(AspectContext context, String key) {
    return context.entry().parameters().containsKey("radius")
        ? context.entry().parameter("radius", DEFAULT_RADIUS)
        : RADIUS_ATTRIBUTES.stream()
            .map(name -> context.kits().attribute(key, name))
            .flatMapToDouble(OptionalDouble::stream)
            .findFirst()
            .orElse(DEFAULT_RADIUS);
  }

  private static double manaPerUse(AspectContext context, String key, double second) {
    if (context.entry().parameters().containsKey("manaPerUse")) {
      return context.entry().parameter("manaPerUse", 0);
    }
    OptionalDouble flat =
        MANA_ATTRIBUTES.stream()
            .map(name -> context.kits().attribute(key, name))
            .flatMapToDouble(OptionalDouble::stream)
            .findFirst();
    if (flat.isPresent()) {
      return flat.getAsDouble();
    }
    OptionalDouble perCharge = context.kits().attribute(key, MANA_PER_CHARGE);
    OptionalInt charges = context.hero().chargesAt(context.names(), second);
    return perCharge.isPresent() && charges.isPresent()
        ? perCharge.getAsDouble() * charges.getAsInt()
        : 0;
  }

  private static boolean within(
      ReplayPlayerTrackDomain userTrack,
      OptionalInt userSample,
      ReplayPlayerTrackDomain allyTrack,
      double second,
      double radius) {
    OptionalInt allySample = nearest(allyTrack, second);
    if (userSample.isEmpty() || allySample.isEmpty() || !alive(allyTrack, allySample.getAsInt())) {
      return false;
    }
    double dx = userTrack.getX()[userSample.getAsInt()] - allyTrack.getX()[allySample.getAsInt()];
    double dy = userTrack.getY()[userSample.getAsInt()] - allyTrack.getY()[allySample.getAsInt()];
    return Math.hypot(dx, dy) <= radius;
  }

  private static boolean alive(ReplayPlayerTrackDomain track, int sample) {
    return track.getFlags() == null
        || sample >= track.getFlags().length
        || (track.getFlags()[sample] & ALIVE_FLAG) != 0;
  }

  /** The index of the track sample nearest the second that holds a position. */
  private static OptionalInt nearest(ReplayPlayerTrackDomain track, double second) {
    if (track == null
        || track.getStartSeconds() == null
        || track.getStepSeconds() == null
        || track.getStepSeconds() <= 0
        || track.getX() == null
        || track.getY() == null) {
      return OptionalInt.empty();
    }
    int index = (int) Math.round((second - track.getStartSeconds()) / track.getStepSeconds());
    return index < 0 || index >= track.getX().length || index >= track.getY().length
        ? OptionalInt.empty()
        : OptionalInt.of(index);
  }
}
