package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * The creep kills in the window before the hero's first item of the entry and the window after it,
 * and whether the two can be compared. The farming aspects share it: the damage aspects add their
 * value only to a comparison that holds.
 */
record FarmWindows(
    Window before,
    Window after,
    boolean comparable,
    List<String> flags,
    double bought,
    double ownedTo,
    double ownedMinutes) {
  static final String NO_BEFORE_WINDOW = "NO_BEFORE_WINDOW";
  static final String SHORT_AFTER_WINDOW = "SHORT_AFTER_WINDOW";
  static final String BEFORE = "BEFORE";
  static final String AFTER = "AFTER";
  static final double SECONDS_PER_MINUTE = 60;

  record Window(String label, double from, double to, List<CreepKillDomain> kills) {
    double seconds() {
      return to - from;
    }

    double minutes() {
      return seconds() / SECONDS_PER_MINUTE;
    }

    double gold() {
      return kills.stream().mapToInt(k -> k.getGold() == null ? 0 : k.getGold()).sum();
    }

    double goldPerMinute() {
      return gold() / minutes();
    }
  }

  static FarmWindows of(AspectContext context) {
    ItemEfficiencyEntry entry = context.entry();
    HeroInputs hero = context.hero();
    double bought = context.bought();
    double end = context.gameEndSeconds();
    double length = entry.parameter("windowMinutes", 5) * SECONDS_PER_MINUTE;
    double ownedTo = Math.min(end, hero.ownedUntil(context.names(), end));
    Window before = window(hero, BEFORE, Math.max(0, bought - length), bought);
    Window after = window(hero, AFTER, bought, Math.min(ownedTo, bought + length));
    List<String> flags = new ArrayList<>();
    boolean comparable =
        before.seconds() > 0 && before.seconds() >= entry.parameter("minBeforeSeconds", 120.0);
    if (!comparable) {
      flags.add(NO_BEFORE_WINDOW);
    }
    if (after.seconds() <= 0 || after.seconds() < entry.parameter("minAfterSeconds", 60.0)) {
      flags.add(SHORT_AFTER_WINDOW);
      comparable = false;
    }
    double ownedMinutes = Math.max(0, ownedTo - bought) / SECONDS_PER_MINUTE;
    return new FarmWindows(before, after, comparable, flags, bought, ownedTo, ownedMinutes);
  }

  private static Window window(HeroInputs hero, String label, double from, double to) {
    List<CreepKillDomain> kills =
        hero.creepKills().stream()
            .filter(k -> k.getSeconds() != null && !Boolean.TRUE.equals(k.getDenied()))
            .filter(k -> k.getSeconds() >= from && k.getSeconds() < to)
            .toList();
    return new Window(label, from, to, kills);
  }
}
