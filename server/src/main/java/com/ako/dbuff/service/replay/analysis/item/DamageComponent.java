package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;

/**
 * What an item adds in damage to enemy heroes, as a second part of its worth next to the gold it
 * earns. Two parts, both first guesses:
 *
 * <ul>
 *   <li>the item's own damage (chain lightning, static charge, cleave): damage entries of the
 *       combat log whose inflictor is the item, exact, from the purchase to the end of ownership;
 *   <li>the passive uplift (attack damage and speed): the hero's damage per minute to heroes in the
 *       window after the purchase less the window before, less the item's own damage per minute,
 *       kept at 0 or more and carried over the whole ownership. The stage of the game confounds
 *       this: a hero deals more damage later whatever he holds.
 * </ul>
 *
 * Only damage to heroes counts; what the item does to creeps is already in the creep gold.
 *
 * @param procDamage the item's own damage in the whole ownership
 * @param passiveDamage the uplift per minute times the minutes owned; 0 when it cannot be told
 * @param beforePerMinute hero damage per minute before the purchase; null when it cannot be told
 * @param afterPerMinute the same after the purchase; null when it cannot be told
 */
record DamageComponent(
    double procDamage, double passiveDamage, Double beforePerMinute, Double afterPerMinute) {

  private static final double SECONDS_PER_MINUTE = 60;

  /** Whether the before and after rates were measured, so the passive uplift means something. */
  boolean compared() {
    return beforePerMinute != null && afterPerMinute != null;
  }

  double value(double goldPerThousandDamage) {
    return (procDamage + passiveDamage) * goldPerThousandDamage / 1000;
  }

  static DamageComponent estimate(
      ItemEfficiencyEntry entry,
      HeroInputs hero,
      List<String> names,
      double bought,
      double ownedTo) {
    double length = entry.parameter("damageWindowMinutes", 5.0) * SECONDS_PER_MINUTE;
    double beforeFrom = Math.max(0, bought - length);
    double afterTo = Math.min(ownedTo, bought + length);
    double beforeSeconds = bought - beforeFrom;
    double afterSeconds = afterTo - bought;
    double procDamage = hero.itemDamage(names, bought, ownedTo);
    double owned = Math.max(0, ownedTo - bought) / SECONDS_PER_MINUTE;
    boolean measured =
        beforeSeconds > 0
            && beforeSeconds >= entry.parameter("minBeforeSeconds", 120.0)
            && afterSeconds > 0
            && afterSeconds >= entry.parameter("minAfterSeconds", 60.0);
    if (!measured) {
      return new DamageComponent(procDamage, 0, null, null);
    }
    double before = hero.heroDamage(beforeFrom, bought) / (beforeSeconds / SECONDS_PER_MINUTE);
    double after = hero.heroDamage(bought, afterTo) / (afterSeconds / SECONDS_PER_MINUTE);
    double procRate = hero.itemDamage(names, bought, afterTo) / (afterSeconds / SECONDS_PER_MINUTE);
    double uplift = Math.max(0, after - before - procRate);
    return new DamageComponent(procDamage, uplift * owned, before, after);
  }
}
