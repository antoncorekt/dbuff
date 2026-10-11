package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Says for one hero in one fight which of his abilities and items he used, and when not, why. It
 * reads his state timeline and the combat-log events he caused, indexed by the ability or item
 * name.
 */
public final class FightAbilityAssessor {
  private static final String ITEM_PREFIX = "item_";

  /** Carried for upkeep or travel, not for fighting; teleports are covered by AbsenceReason. */
  private static final Set<String> NOT_FOR_FIGHTING =
      Set.of(
          "item_tpscroll",
          "item_travel_boots",
          "item_travel_boots_2",
          "item_tango",
          "item_flask",
          "item_clarity",
          "item_enchanted_mango",
          "item_faerie_fire",
          "item_smoke_of_deceit",
          "item_ward_observer",
          "item_ward_sentry",
          "item_ward_dispenser",
          "item_bottle");

  private final int earlyDeathSeconds;

  public FightAbilityAssessor(int earlyDeathSeconds) {
    this.earlyDeathSeconds = earlyDeathSeconds;
  }

  /**
   * @param manaAtStart null when it is not known
   */
  public record Use(
      String name,
      boolean item,
      int level,
      int cooldownLeft,
      int manaCost,
      Integer manaAtStart,
      int casts,
      Double firstCast,
      long damage,
      Double lastCastBefore,
      Boolean lastCastOnCreeps,
      NonCastReason reason) {}

  /**
   * @param byName the events the hero caused (ability and item uses and damage) by inflictor name,
   *     in time order, over the whole match
   * @param deathSeconds when the hero died during the match
   */
  public List<Use> assess(
      double start,
      double end,
      HeroStateTimeline state,
      Integer manaAtStart,
      Map<String, List<CombatEvent>> byName,
      List<Double> deathSeconds) {
    boolean diesEarly =
        deathSeconds.stream().anyMatch(d -> d >= start && d <= start + earlyDeathSeconds);
    List<Use> uses = new ArrayList<>();
    for (String name : meant(start, state, byName)) {
      boolean item = name.startsWith(ITEM_PREFIX);
      List<CombatEvent> events = byName.getOrDefault(name, List.of());
      int cooldownLeft = (int) Math.ceil(state.cooldownLeft(name, start));
      int cost = state.manaCost(name, start);
      List<CombatEvent> casts =
          events.stream()
              .filter(e -> isCast(e) && e.gameSecond() >= start && e.gameSecond() <= end)
              .toList();
      long damage =
          events.stream()
              .filter(
                  e ->
                      e.kind() == CombatEventKind.DAMAGE
                          && (e.flags() & CombatEvent.TARGET_HERO) != 0
                          && e.gameSecond() >= start
                          && e.gameSecond() <= end)
              .mapToLong(CombatEvent::value)
              .sum();
      NonCastReason reason =
          casts.isEmpty() ? reason(cooldownLeft, cost, manaAtStart, diesEarly) : null;
      Double lastCast = casts.isEmpty() ? lastCastBefore(events, start) : null;
      uses.add(
          new Use(
              name,
              item,
              item ? 0 : state.level(name, start),
              cooldownLeft,
              cost,
              manaAtStart,
              casts.size(),
              casts.isEmpty() ? null : casts.getFirst().gameSecond(),
              damage,
              lastCast,
              lastCast == null ? null : onlyCreeps(events, lastCast),
              reason));
    }
    return uses;
  }

  private static NonCastReason reason(
      int cooldownLeft, int cost, Integer manaAtStart, boolean diesEarly) {
    if (cooldownLeft > 0) {
      return NonCastReason.ON_COOLDOWN;
    }
    if (manaAtStart != null && cost > 0 && manaAtStart < cost) {
      return NonCastReason.NO_MANA;
    }
    return diesEarly ? NonCastReason.DEAD_EARLY : NonCastReason.READY_NOT_CAST;
  }

  /** The learned abilities and held items he was meant to use, abilities first. */
  private static Set<String> meant(
      double start, HeroStateTimeline state, Map<String, List<CombatEvent>> byName) {
    Set<String> names = new LinkedHashSet<>();
    for (String ability : state.learnedAbilities()) {
      if (state.level(ability, start) >= 1 && usable(ability, start, state, byName)) {
        names.add(ability);
      }
    }
    for (String item : state.itemsAt(start)) {
      if (!NOT_FOR_FIGHTING.contains(item) && usable(item, start, state, byName)) {
        names.add(item);
      }
    }
    return names;
  }

  /** Passives and toggles never show a cooldown, a cost or a use, so they are left out. */
  private static boolean usable(
      String name, double start, HeroStateTimeline state, Map<String, List<CombatEvent>> byName) {
    return state.everOnCooldown(name)
        || state.manaCost(name, start) > 0
        || byName.getOrDefault(name, List.of()).stream().anyMatch(FightAbilityAssessor::isCast);
  }

  private static boolean isCast(CombatEvent e) {
    return e.kind() == CombatEventKind.ABILITY || e.kind() == CombatEventKind.ITEM;
  }

  private static Double lastCastBefore(List<CombatEvent> events, double start) {
    Double last = null;
    for (CombatEvent e : events) {
      if (isCast(e) && e.gameSecond() < start) {
        last = e.gameSecond();
      }
    }
    return last;
  }

  /** Whether what the cast hit, in the few seconds after it, was creeps and no hero. */
  private static boolean onlyCreeps(List<CombatEvent> events, double castSecond) {
    boolean creep = false;
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DAMAGE
          || e.gameSecond() < castSecond - 1
          || e.gameSecond() > castSecond + 3) {
        continue;
      }
      if ((e.flags() & CombatEvent.TARGET_HERO) != 0) {
        return false;
      }
      creep = true;
    }
    return creep;
  }
}
