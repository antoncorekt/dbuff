package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds fights in the combat log: hero-to-hero damage and hero deaths that follow each other
 * closely are one fight. A fight with three deaths is the teamfight OpenDota would also find;
 * smaller ones, and ones where nobody died, are what OpenDota misses.
 */
public final class FightDetector {

  /**
   * @param mergeGapSeconds hostile events closer than this are one fight
   * @param minDamagePerSide hero damage each side needs for a fight with no death
   * @param minHeroesPerSide heroes each side needs for a fight with no death
   * @param intensityWindowSeconds damage is fight activity only when the hero damage within this
   *     window around it reaches {@code intensityMinDamage}; lane harassment stays below it
   */
  public record Tuning(
      int mergeGapSeconds,
      int minDamagePerSide,
      int minHeroesPerSide,
      int intensityWindowSeconds,
      int intensityMinDamage) {}

  /** One hero death inside a fight; a player is a replay player index 0-9. */
  public record Death(double second, int victim) {}

  /**
   * A fight found in the log. Damage maps are per player index; {@code participants} are the
   * players who dealt or took hero damage or died.
   */
  public record Detected(
      FightType type,
      double start,
      double end,
      List<Death> deaths,
      Map<Integer, Long> damageDealt,
      Map<Integer, Long> damageTaken,
      Set<Integer> participants) {

    public Double firstDeath() {
      return deaths.isEmpty() ? null : deaths.getFirst().second();
    }
  }

  private static final int TEAM_SIZE = 5;
  private static final int TEAMFIGHT_DEATHS = 3;

  private final Tuning tuning;

  public FightDetector(Tuning tuning) {
    this.tuning = tuning;
  }

  /**
   * @param playerOf maps a combat-log hero name to its player index 0-9; heroes not in it (a clone,
   *     a summon) are ignored
   */
  public List<Detected> detect(List<CombatEvent> events, Map<String, Integer> playerOf) {
    List<CombatEvent> hostile = intense(hostile(events, playerOf));
    List<Detected> fights = new ArrayList<>();
    List<CombatEvent> cluster = new ArrayList<>();
    for (CombatEvent event : hostile) {
      if (!cluster.isEmpty()
          && event.gameSecond() - cluster.getLast().gameSecond() > tuning.mergeGapSeconds()) {
        classify(cluster, playerOf).ifPresent(fights::add);
        cluster = new ArrayList<>();
      }
      cluster.add(event);
    }
    if (!cluster.isEmpty()) {
      classify(cluster, playerOf).ifPresent(fights::add);
    }
    return fights;
  }

  private static List<CombatEvent> hostile(
      List<CombatEvent> events, Map<String, Integer> playerOf) {
    return events.stream()
        .filter(
            e ->
                e.gameSecond() >= 0
                    && ((e.kind() == CombatEventKind.DAMAGE
                            && e.attackerIsRealHero()
                            && e.targetIsRealHero()
                            && playerOf.containsKey(e.attacker())
                            && playerOf.containsKey(e.target())
                            && team(playerOf.get(e.attacker())) != team(playerOf.get(e.target())))
                        || (e.kind() == CombatEventKind.DEATH
                            && e.targetIsRealHero()
                            && playerOf.containsKey(e.target()))))
        .sorted(Comparator.comparingDouble(CombatEvent::gameSecond))
        .toList();
  }

  /** Keeps deaths and the damage that comes in a burst, dropping scattered pokes. */
  private List<CombatEvent> intense(List<CombatEvent> hostile) {
    double half = tuning.intensityWindowSeconds() / 2.0;
    List<CombatEvent> damage =
        hostile.stream().filter(e -> e.kind() == CombatEventKind.DAMAGE).toList();
    List<CombatEvent> kept = new ArrayList<>();
    int from = 0;
    int to = 0;
    long window = 0;
    for (CombatEvent e : hostile) {
      if (e.kind() == CombatEventKind.DEATH) {
        kept.add(e);
        continue;
      }
      while (to < damage.size() && damage.get(to).gameSecond() <= e.gameSecond() + half) {
        window += damage.get(to++).value();
      }
      while (damage.get(from).gameSecond() < e.gameSecond() - half) {
        window -= damage.get(from++).value();
      }
      if (window >= tuning.intensityMinDamage()) {
        kept.add(e);
      }
    }
    return kept;
  }

  private java.util.Optional<Detected> classify(
      List<CombatEvent> cluster, Map<String, Integer> playerOf) {
    List<Death> deaths = new ArrayList<>();
    Map<Integer, Long> dealt = new TreeMap<>();
    Map<Integer, Long> taken = new TreeMap<>();
    Set<Integer> participants = new HashSet<>();
    for (CombatEvent e : cluster) {
      int victim = playerOf.get(e.target());
      participants.add(victim);
      if (e.kind() == CombatEventKind.DEATH) {
        deaths.add(new Death(e.gameSecond(), victim));
      } else {
        int attacker = playerOf.get(e.attacker());
        participants.add(attacker);
        dealt.merge(attacker, (long) e.value(), Long::sum);
        taken.merge(victim, (long) e.value(), Long::sum);
      }
    }
    FightType type = type(deaths.size(), dealt, participants);
    if (type == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new Detected(
            type,
            cluster.getFirst().gameSecond(),
            cluster.getLast().gameSecond(),
            deaths,
            dealt,
            taken,
            participants));
  }

  private FightType type(int deaths, Map<Integer, Long> dealt, Set<Integer> participants) {
    if (deaths >= TEAMFIGHT_DEATHS) {
      return FightType.TEAMFIGHT;
    }
    if (deaths > 0) {
      return participants.size() >= 2 ? FightType.SKIRMISH : null;
    }
    long radiant = sideDamage(dealt, 0);
    long dire = sideDamage(dealt, 1);
    boolean enough =
        radiant >= tuning.minDamagePerSide()
            && dire >= tuning.minDamagePerSide()
            && heroes(participants, 0) >= tuning.minHeroesPerSide()
            && heroes(participants, 1) >= tuning.minHeroesPerSide();
    return enough ? FightType.ENGAGEMENT : null;
  }

  private static long sideDamage(Map<Integer, Long> dealt, int team) {
    return dealt.entrySet().stream()
        .filter(e -> team(e.getKey()) == team)
        .mapToLong(Map.Entry::getValue)
        .sum();
  }

  private static long heroes(Set<Integer> participants, int team) {
    return participants.stream().filter(p -> team(p) == team).count();
  }

  /** 0 for Radiant (players 0-4), 1 for Dire. */
  public static int team(int player) {
    return player < TEAM_SIZE ? 0 : 1;
  }
}
