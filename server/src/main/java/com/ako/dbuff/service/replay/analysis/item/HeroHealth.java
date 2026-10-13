package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * How much health and mana a hero had, from what the replay stores: the track gives percentages
 * every few seconds, the damage entries give his exact health right after a hit. Max health is
 * never stored, so it is estimated where a hit lands next to a track sample (health after the hit
 * divided by the sample's percentage).
 */
final class HeroHealth {
  private static final double SAMPLE_TOLERANCE_SECONDS = 1.0;
  private static final int LOWEST_USABLE_PERCENT = 15;
  private static final int FULL_PERCENT = 100;
  /** At 0 percent the maximum cannot be read, but everything restored is useful. */
  private static final int EMPTY_MAX = Integer.MAX_VALUE;

  private record Hit(double second, int amount, int healthAfter) {}

  private record Estimate(double second, double maxHealth) {}

  /** Mana at a track sample and the most the hero could hold then. */
  record Mana(int current, int max) {
    int missing() {
      return Math.max(0, max - current);
    }
  }

  private final ReplayPlayerTrackDomain track;
  private final List<Hit> hits = new ArrayList<>();
  private final List<Double> deaths = new ArrayList<>();
  private final List<Estimate> estimates = new ArrayList<>();

  HeroHealth(String hero, List<CombatEvent> events, ReplayPlayerTrackDomain track) {
    this.track = track;
    for (CombatEvent e : events) {
      if (e.kind() == CombatEventKind.DAMAGE && hero.equals(e.target()) && e.targetIsRealHero()) {
        if (!hero.equals(e.attacker())) {
          hits.add(new Hit(e.gameSecond(), e.value(), e.health()));
        }
      } else if (e.kind() == CombatEventKind.DEATH
          && hero.equals(e.target())
          && e.targetIsRealHero()) {
        deaths.add(e.gameSecond());
      }
    }
    collectEstimates();
  }

  private void collectEstimates() {
    if (!hasGrid() || track.getHpPercent() == null) {
      return;
    }
    for (int k = 0; k < hits.size(); k++) {
      Hit hit = hits.get(k);
      int index = (int) Math.round((hit.second() - track.getStartSeconds()) / step());
      if (hit.healthAfter() <= 0 || index < 0 || index >= track.getHpPercent().length) {
        continue;
      }
      double sampleSecond = track.getStartSeconds() + index * (double) step();
      int percent = track.getHpPercent()[index];
      if (Math.abs(hit.second() - sampleSecond) > SAMPLE_TOLERANCE_SECONDS
          || percent < LOWEST_USABLE_PERCENT
          || percent >= FULL_PERCENT
          || hitBetween(k, hit.second(), sampleSecond)) {
        continue;
      }
      estimates.add(new Estimate(hit.second(), hit.healthAfter() * 100.0 / percent));
    }
  }

  private boolean hitBetween(int k, double a, double b) {
    double from = Math.min(a, b);
    double to = Math.max(a, b);
    for (int other = Math.max(0, k - 2); other < Math.min(hits.size(), k + 3); other++) {
      double t = hits.get(other).second();
      if (other != k && t >= from && t <= to) {
        return true;
      }
    }
    return false;
  }

  /** The median estimate within {@code windowSeconds} of the second; empty when there is none. */
  OptionalInt maxHealth(double second, double windowSeconds) {
    double[] near =
        estimates.stream()
            .filter(e -> Math.abs(e.second() - second) <= windowSeconds)
            .mapToDouble(Estimate::maxHealth)
            .sorted()
            .toArray();
    return near.length == 0 ? OptionalInt.empty() : OptionalInt.of((int) near[near.length / 2]);
  }

  /**
   * His health at the second: the one the last hit left when it was at most {@code lookbackSeconds}
   * ago, else the track percentage of the sample before times his max health.
   */
  OptionalInt health(double second, double lookbackSeconds, double maxHealthWindowSeconds) {
    Hit last = null;
    for (Hit hit : hits) {
      if (hit.second() > second) {
        break;
      }
      last = hit;
    }
    if (last != null && second - last.second() <= lookbackSeconds && last.healthAfter() > 0) {
      return OptionalInt.of(last.healthAfter());
    }
    OptionalInt max = maxHealth(second, maxHealthWindowSeconds);
    int index = sampleBefore(second);
    if (max.isEmpty()
        || index < 0
        || track.getHpPercent() == null
        || index >= track.getHpPercent().length) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(track.getHpPercent()[index] * max.getAsInt() / 100);
  }

  /** The mana of the track sample at or before the second; null when the track has no mana. */
  Mana mana(double second) {
    int index = sampleBefore(second);
    if (index < 0
        || track.getMana() == null
        || track.getManaPercent() == null
        || index >= track.getMana().length
        || index >= track.getManaPercent().length) {
      return null;
    }
    int percent = track.getManaPercent()[index];
    int current = track.getMana()[index];
    return percent <= 0
        ? new Mana(current, EMPTY_MAX)
        : new Mana(current, (int) Math.round(current * 100.0 / percent));
  }

  /** Damage the hero took from others in {@code (from, to]}. */
  int damage(double from, double to) {
    return hits.stream()
        .filter(h -> h.second() > from && h.second() <= to)
        .mapToInt(Hit::amount)
        .sum();
  }

  boolean diedBetween(double from, double to) {
    return deaths.stream().anyMatch(t -> t > from && t <= to);
  }

  private int sampleBefore(double second) {
    if (!hasGrid() || track.getSampleCount() == null) {
      return -1;
    }
    int index = (int) Math.floor((second - track.getStartSeconds()) / step());
    return index < 0 || index >= track.getSampleCount() ? -1 : index;
  }

  private boolean hasGrid() {
    return track != null
        && track.getStartSeconds() != null
        && track.getStepSeconds() != null
        && track.getStepSeconds() > 0;
  }

  private int step() {
    return track.getStepSeconds();
  }
}
