package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.List;
import java.util.OptionalDouble;

/**
 * What a death costs a hero: the gold lost, plus the seconds spent dead at his income per second
 * then.
 */
final class DeathCost {
  private static final int ALIVE_FLAG = 1;
  private static final double SECONDS_PER_MINUTE = 60;

  /**
   * @param goldLost gold lost on the death
   * @param respawnSeconds the seconds from the death to the first track sample alive
   * @param goldPerMinute his income that minute
   * @param estimated true when the gold lost or the respawn time could not be read and was assumed
   */
  record Cost(double goldLost, double respawnSeconds, double goldPerMinute, boolean estimated) {
    double value() {
      return goldLost + respawnSeconds * goldPerMinute / SECONDS_PER_MINUTE;
    }
  }

  private final HeroInputs hero;
  private final WorthPrices prices;
  private final double[] lostByMinute;
  private final double[] earnedByMinute;
  private final List<CombatEvent> deaths;

  DeathCost(HeroInputs hero, WorthPrices prices) {
    this.hero = hero;
    this.prices = prices;
    var rows = hero.match().minutesOf(hero.slot());
    this.lostByMinute = MinuteSeries.during(rows, MinuteSeries::lostToDeath);
    this.earnedByMinute = MinuteSeries.during(rows, MinuteSeries::earned);
    this.deaths =
        hero.events().stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && hero.hero().equals(e.target()))
            .filter(CombatEvent::targetIsRealHero)
            .toList();
  }

  /** A death that happened at the second. */
  Cost at(double second) {
    OptionalDouble respawn = respawnAfter(second);
    return new Cost(
        MinuteSeries.at(lostByMinute, second),
        respawn.orElse(prices.respawnSeconds()),
        MinuteSeries.at(earnedByMinute, second),
        respawn.isEmpty());
  }

  /**
   * A death that did not happen at the second. The gold lost is the average of his real deaths,
   * else the gold he held then; the respawn time is the average of his real ones, else the assumed
   * one.
   */
  Cost avoided(double second) {
    OptionalDouble lost =
        deaths.stream().mapToDouble(d -> MinuteSeries.at(lostByMinute, d.gameSecond())).average();
    OptionalDouble respawn =
        deaths.stream()
            .map(d -> respawnAfter(d.gameSecond()))
            .flatMapToDouble(OptionalDouble::stream)
            .average();
    OptionalDouble held = goldHeld(second);
    return new Cost(
        lost.orElse(held.orElse(0)),
        respawn.orElse(prices.respawnSeconds()),
        MinuteSeries.at(earnedByMinute, second),
        (lost.isEmpty() && held.isEmpty()) || respawn.isEmpty());
  }

  private OptionalDouble respawnAfter(double second) {
    ReplayPlayerTrackDomain track = hero.track();
    if (!hasGrid(track) || track.getFlags() == null) {
      return OptionalDouble.empty();
    }
    int step = track.getStepSeconds();
    int first = (int) Math.ceil((second - track.getStartSeconds()) / step);
    for (int i = Math.max(0, first); i < track.getFlags().length; i++) {
      if ((track.getFlags()[i] & ALIVE_FLAG) != 0) {
        return OptionalDouble.of(Math.max(0, track.getStartSeconds() + (double) i * step - second));
      }
    }
    return OptionalDouble.empty();
  }

  private OptionalDouble goldHeld(double second) {
    ReplayPlayerTrackDomain track = hero.track();
    if (!hasGrid(track) || track.getGold() == null) {
      return OptionalDouble.empty();
    }
    int i = (int) Math.floor((second - track.getStartSeconds()) / track.getStepSeconds());
    return i >= 0 && i < track.getGold().length
        ? OptionalDouble.of(track.getGold()[i])
        : OptionalDouble.empty();
  }

  private static boolean hasGrid(ReplayPlayerTrackDomain track) {
    return track != null
        && track.getStartSeconds() != null
        && track.getStepSeconds() != null
        && track.getStepSeconds() > 0;
  }
}
