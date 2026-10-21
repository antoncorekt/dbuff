package com.ako.dbuff.service.replay.analysis.health;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.List;
import java.util.OptionalDouble;

/**
 * What a death costs a hero: the gold lost, plus the seconds spent dead at his income per second
 * then.
 */
public class DeathCost {
  private static final int ALIVE_FLAG = 1;
  private static final double SECONDS_PER_MINUTE = 60;

  /**
   * @param goldLost gold lost on the death
   * @param respawnSeconds the seconds from the death to the first track sample alive
   * @param goldPerMinute his income that minute
   * @param estimated true when the gold lost or the respawn time could not be read and was assumed
   */
  public record Cost(
      double goldLost, double respawnSeconds, double goldPerMinute, boolean estimated) {
    public double value() {
      return goldLost + respawnSeconds * goldPerMinute / SECONDS_PER_MINUTE;
    }
  }

  private final ReplayPlayerTrackDomain track;
  private final double assumedRespawnSeconds;
  private final double[] lostByMinute;
  private final double[] earnedByMinute;
  private final List<CombatEvent> deaths;

  /**
   * @param hero the hero's name in the events
   * @param events his combat events
   * @param track his track; may be null
   * @param lostByMinute gold lost to death in each game minute
   * @param earnedByMinute gold earned in each game minute
   * @param assumedRespawnSeconds respawn time assumed when the track cannot tell it
   */
  public DeathCost(
      String hero,
      List<CombatEvent> events,
      ReplayPlayerTrackDomain track,
      double[] lostByMinute,
      double[] earnedByMinute,
      double assumedRespawnSeconds) {
    this.track = track;
    this.assumedRespawnSeconds = assumedRespawnSeconds;
    this.lostByMinute = lostByMinute;
    this.earnedByMinute = earnedByMinute;
    this.deaths =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && hero.equals(e.target()))
            .filter(CombatEvent::targetIsRealHero)
            .toList();
  }

  /** A death that happened at the second. */
  public Cost at(double second) {
    OptionalDouble respawn = respawnAfter(second);
    return new Cost(
        lostAt(second),
        respawn.orElse(assumedRespawnSeconds),
        at(earnedByMinute, second),
        respawn.isEmpty());
  }

  private double lostAt(double second) {
    long sameMinute =
        deaths.stream().filter(d -> minuteOf(d.gameSecond()) == minuteOf(second)).count();
    return at(lostByMinute, second) / Math.max(1, sameMinute);
  }

  /**
   * A death that did not happen at the second. The gold lost is the average of his real deaths,
   * else the gold he held then; the respawn time is the average of his real ones, else the assumed
   * one.
   */
  public Cost avoided(double second) {
    OptionalDouble lost = deaths.stream().mapToDouble(d -> lostAt(d.gameSecond())).average();
    OptionalDouble respawn =
        deaths.stream()
            .map(d -> respawnAfter(d.gameSecond()))
            .flatMapToDouble(OptionalDouble::stream)
            .average();
    OptionalDouble held = goldHeld(second);
    return new Cost(
        lost.orElse(held.orElse(0)),
        respawn.orElse(assumedRespawnSeconds),
        at(earnedByMinute, second),
        lost.isEmpty() || respawn.isEmpty());
  }

  private OptionalDouble respawnAfter(double second) {
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
    if (!hasGrid(track) || track.getGold() == null) {
      return OptionalDouble.empty();
    }
    int i = (int) Math.floor((second - track.getStartSeconds()) / track.getStepSeconds());
    return i >= 0 && i < track.getGold().length
        ? OptionalDouble.of(track.getGold()[i])
        : OptionalDouble.empty();
  }

  private static int minuteOf(double second) {
    return (int) Math.floor(second / SECONDS_PER_MINUTE);
  }

  private static double at(double[] series, double second) {
    int minute = minuteOf(second);
    return minute >= 0 && minute < series.length ? series[minute] : 0;
  }

  private static boolean hasGrid(ReplayPlayerTrackDomain track) {
    return track != null
        && track.getStartSeconds() != null
        && track.getStepSeconds() != null
        && track.getStepSeconds() > 0;
  }
}
