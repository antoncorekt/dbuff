package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.vision.DetectionLog;
import com.ako.dbuff.service.replay.analysis.vision.InvisibilityLog;
import com.ako.dbuff.service.replay.analysis.vision.InvisibleHeroes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the vision aspect reads across heroes: the ward log, when each hero was invisible, the dust
 * and gem holdings, and every hero's track for positions.
 *
 * @param abilities the invisibility abilities each slot owned
 */
public record VisionFacts(
    List<WardEventDomain> wards,
    DetectionLog detection,
    InvisibilityLog invisibility,
    Map<Long, Set<String>> abilities,
    Map<Long, ReplayPlayerTrackDomain> tracks) {

  private static final InvisibleHeroes NONE =
      new InvisibleHeroes() {
        @Override
        public Map<String, Source> abilities() {
          return Map.of();
        }

        @Override
        public Set<String> items() {
          return Set.of();
        }
      };

  public static final VisionFacts EMPTY =
      new VisionFacts(
          List.of(),
          new DetectionLog(List.of(), Map.of(), List.of()),
          new InvisibilityLog(List.of(), Map.of(), NONE),
          Map.of(),
          Map.of());

  static VisionFacts of(
      List<WardEventDomain> wards,
      List<CombatEvent> events,
      Map<String, Long> slotByHero,
      List<ReplayStateChangeDomain> changes,
      Map<Long, ReplayPlayerTrackDomain> tracks,
      InvisibleHeroes sources) {
    Map<Long, Set<String>> owned = new HashMap<>();
    changes.stream()
        .filter(c -> StateChangeKind.ABILITY_LEVEL.name().equals(c.getKind()))
        .filter(c -> c.getPlayerSlot() != null && c.getAbilityLevel() != null)
        .filter(c -> c.getAbilityLevel() > 0 && sources.abilities().containsKey(c.getName()))
        .forEach(
            c ->
                owned
                    .computeIfAbsent(c.getPlayerSlot(), s -> new java.util.HashSet<>())
                    .add(c.getName()));
    return new VisionFacts(
        wards,
        new DetectionLog(events, slotByHero, changes),
        new InvisibilityLog(events, slotByHero, sources),
        owned,
        tracks);
  }

  /** The world position of the slot's hero in the track sample nearest the second; null if none. */
  public double[] positionOf(long slot, double second) {
    ReplayPlayerTrackDomain t = tracks.get(slot);
    if (t == null
        || t.getX() == null
        || t.getY() == null
        || t.getStartSeconds() == null
        || t.getStepSeconds() == null
        || t.getStepSeconds() <= 0) {
      return null;
    }
    int i = (int) Math.round((second - t.getStartSeconds()) / t.getStepSeconds());
    i = Math.max(0, Math.min(Math.min(t.getX().length, t.getY().length) - 1, i));
    return t.getX().length == 0 ? null : new double[] {t.getX()[i], t.getY()[i]};
  }
}
