package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Everything stored about one hero that item evaluators read. Item names are the replay's, with the
 * {@code item_} prefix.
 *
 * @param events the combat log entries of or about the hero (item uses by him, damage to him, his
 *     deaths, his gold and experience), in log order
 * @param track null when the hero has none
 * @param match what the worth model reads across heroes
 * @param matchId the match the facts are of
 */
public record HeroInputs(
    long slot,
    String hero,
    List<ReplayStateChangeDomain> changes,
    List<CombatEvent> events,
    ReplayPlayerTrackDomain track,
    List<CreepKillDomain> creepKills,
    List<ActivitySegment> activity,
    MatchFacts match,
    long matchId) {

  public HeroInputs(
      long slot,
      String hero,
      List<ReplayStateChangeDomain> changes,
      List<CombatEvent> events,
      ReplayPlayerTrackDomain track,
      List<CreepKillDomain> creepKills,
      List<ActivitySegment> activity) {
    this(slot, hero, changes, events, track, creepKills, activity, MatchFacts.EMPTY, 0);
  }

  public static final String ITEM_PREFIX = "item_";

  public static String replayName(String itemKey) {
    return ITEM_PREFIX + itemKey;
  }

  /** The constants key of a replay item name. */
  public static String itemKey(String replayName) {
    return replayName.substring(ITEM_PREFIX.length());
  }

  /** The names among {@code names} the hero ever held, in the order he first got them. */
  public List<String> held(Collection<String> names) {
    return changes.stream()
        .filter(c -> StateChangeKind.ITEM_ADDED.name().equals(c.getKind()))
        .map(ReplayStateChangeDomain::getName)
        .filter(names::contains)
        .distinct()
        .toList();
  }

  public OptionalDouble firstAdded(Collection<String> names) {
    return changes.stream()
        .filter(c -> StateChangeKind.ITEM_ADDED.name().equals(c.getKind()))
        .filter(c -> names.contains(c.getName()))
        .mapToDouble(ReplayStateChangeDomain::getGameSeconds)
        .findFirst();
  }

  /** The second the hero last let go of every one of the items, or the game end if he kept one. */
  public double ownedUntil(Collection<String> names, double gameEnd) {
    int held = 0;
    double lastRemoved = 0;
    for (ReplayStateChangeDomain c : changes) {
      if (!names.contains(c.getName())) {
        continue;
      }
      if (StateChangeKind.ITEM_ADDED.name().equals(c.getKind())) {
        held++;
      } else if (StateChangeKind.ITEM_REMOVED.name().equals(c.getKind())) {
        held--;
        lastRemoved = c.getGameSeconds();
      }
    }
    return held > 0 ? gameEnd : lastRemoved;
  }

  /** The uses of the items by the hero, from the combat log, in time order. */
  public List<CombatEvent> uses(Collection<String> names) {
    return events.stream()
        .filter(e -> e.kind() == CombatEventKind.ITEM)
        .filter(e -> hero.equals(e.attacker()) && names.contains(e.inflictor()))
        .toList();
  }

  /** The charge count last seen at or before the second; empty when none was ever seen. */
  public OptionalInt chargesAt(Collection<String> names, double second) {
    OptionalInt last = OptionalInt.empty();
    for (ReplayStateChangeDomain c : changes) {
      if (c.getGameSeconds() > second) {
        break;
      }
      if (StateChangeKind.ITEM_CHARGES.name().equals(c.getKind())
          && names.contains(c.getName())
          && c.getCharges() != null) {
        last = OptionalInt.of(c.getCharges());
      }
    }
    return last;
  }

  /**
   * When the item came off cooldown after a use, from the cooldown the replay showed within {@code
   * withinSeconds} of the use; empty when it showed none.
   */
  public OptionalDouble readyAfterUse(String name, double use, double withinSeconds) {
    return changes.stream()
        .filter(c -> StateChangeKind.COOLDOWN.name().equals(c.getKind()))
        .filter(c -> name.equals(c.getName()) && c.getReadyAtSeconds() != null)
        .filter(c -> c.getGameSeconds() >= use - 1 && c.getGameSeconds() <= use + withinSeconds)
        .mapToDouble(ReplayStateChangeDomain::getReadyAtSeconds)
        .filter(ready -> ready > use)
        .findFirst();
  }

  /** The seconds of {@code [from, to]} the hero spent farming lane or jungle creeps. */
  public double farmingSeconds(double from, double to) {
    return activity.stream()
        .filter(s -> s.activity() == Activity.LANE_FARM || s.activity() == Activity.JUNGLE_FARM)
        .mapToDouble(
            s -> Math.max(0, Math.min(to, s.endSeconds()) - Math.max(from, s.startSeconds())))
        .sum();
  }

  /** Damage the hero dealt to enemy heroes in {@code (from, to]}, from the combat log. */
  public double heroDamage(double from, double to) {
    return dealt(from, to, null);
  }

  /** The part of it that came from the named items (their procs and cleave). */
  public double itemDamage(Collection<String> names, double from, double to) {
    return dealt(from, to, names);
  }

  private double dealt(double from, double to, Collection<String> inflictors) {
    return events.stream()
        .filter(e -> e.kind() == CombatEventKind.DAMAGE && hero.equals(e.attacker()))
        .filter(e -> e.targetIsRealHero() && !hero.equals(e.target()))
        .filter(e -> e.gameSecond() > from && e.gameSecond() <= to)
        .filter(e -> inflictors == null || inflictors.contains(e.inflictor()))
        .mapToDouble(CombatEvent::value)
        .sum();
  }
}
