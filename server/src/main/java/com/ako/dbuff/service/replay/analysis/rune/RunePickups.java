package com.ako.dbuff.service.replay.analysis.rune;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.RuneEventDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.GoldReason;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns the stored rune events into pickups: position, bounty gold and the mid lane flag. Events of
 * one slot, second and rune type are one pickup.
 */
@Slf4j
final class RunePickups {
  private final RuneProperties properties;
  private final Map<Long, String> heroOfSlot;
  private final Map<Long, ReplayPlayerTrackDomain> tracks;
  private final Map<Long, List<ActivitySegment>> segments;
  private final MapLayout layout;
  private final List<CombatEvent> bountyGold;
  private final List<CombatEvent> events;
  private final Set<CombatEvent> paid = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * @param heroOfSlot the combat-log hero name of each player slot
   * @param layout null when the map version is not frozen
   * @param events the combat log: bounty-rune gold entries (none when it keeps no gold reasons),
   *     the rune modifiers and the deaths
   */
  RunePickups(
      RuneProperties properties,
      Map<Long, String> heroOfSlot,
      Map<Long, ReplayPlayerTrackDomain> tracks,
      Map<Long, List<ActivitySegment>> segments,
      MapLayout layout,
      List<CombatEvent> events) {
    this.properties = properties;
    this.heroOfSlot = heroOfSlot;
    this.tracks = tracks;
    this.segments = segments;
    this.layout = layout;
    this.events = events;
    this.bountyGold =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.GOLD)
            .filter(e -> e.goldReason() == GoldReason.BOUNTY_RUNE)
            .toList();
  }

  List<RunePickupDomain> build(long matchId, List<RuneEventDomain> runes, int version) {
    List<RuneEventDomain> inOrder =
        runes.stream()
            .filter(r -> r.getPlayerSlot() != null && r.getEventTime() != null)
            .sorted(
                Comparator.comparing(RuneEventDomain::getEventTime)
                    .thenComparing(RuneEventDomain::getPlayerSlot))
            .toList();
    Map<String, List<RuneEventDomain>> pickups = new LinkedHashMap<>();
    for (RuneEventDomain rune : inOrder) {
      String key =
          rune.getPlayerSlot()
              + ":"
              + rune.getEventTime().intValue()
              + ":"
              + RuneType.of(rune.getRuneType());
      pickups.computeIfAbsent(key, k -> new ArrayList<>()).add(rune);
    }
    List<RunePickupDomain> rows = new ArrayList<>();
    for (List<RuneEventDomain> group : pickups.values()) {
      RuneEventDomain rune = group.get(0);
      long slot = rune.getPlayerSlot();
      int seconds = rune.getEventTime().intValue();
      RuneType type = RuneType.of(rune.getRuneType());
      Zone zone = zoneAt(slot, seconds);
      Double effectEnd = effectEnd(slot, seconds, type);
      rows.add(
          RunePickupDomain.builder()
              .matchId(matchId)
              .playerSlot(slot)
              .team(TeamSide.ofSlot(slot).name())
              .runeIndex(rune.getRuneIndex())
              .runeType(type.name())
              .seconds(seconds)
              .zone(zone == null ? null : zone.kind().name())
              .lane(zone == null || zone.lane() == null ? null : zone.lane().name())
              .gold(type == RuneType.BOUNTY ? goldOf(slot, seconds, group.size()) : null)
              .effectEndSeconds(effectEnd)
              .midLane(midLane(slot, seconds, zone))
              .analysisVersion(version)
              .build());
    }
    int collapsed = inOrder.size() - rows.size();
    if (collapsed > 0) {
      log.info("Match {}: collapsed {} duplicate rune events", matchId, collapsed);
    }
    return rows;
  }

  private Zone zoneAt(long slot, int seconds) {
    ReplayPlayerTrackDomain track = tracks.get(slot);
    if (layout == null
        || track == null
        || track.getSampleCount() == null
        || track.getStartSeconds() == null
        || track.getStepSeconds() == null
        || track.getStepSeconds() <= 0) {
      return null;
    }
    int last = track.getSampleCount() - 1;
    if (last < 0 || track.getX() == null || track.getY() == null) {
      return null;
    }
    long index = Math.round((seconds - track.getStartSeconds()) / (double) track.getStepSeconds());
    int i = (int) Math.max(0, Math.min(last, index));
    return layout.zone(track.getX()[i], track.getY()[i]);
  }

  private Integer goldOf(long slot, int seconds, int events) {
    Integer sum = null;
    for (int i = 0; i < events; i++) {
      Integer gold = goldAt(slot, seconds);
      if (gold != null) {
        sum = sum == null ? gold : sum + gold;
      }
    }
    return sum;
  }

  /** The nearest unused bounty entry of the hero within the tolerance; each pays one pickup. */
  private Integer goldAt(long slot, int seconds) {
    String hero = heroOfSlot.get(slot);
    CombatEvent best = null;
    for (CombatEvent e : bountyGold) {
      if (e.target().equals(hero)
          && !paid.contains(e)
          && Math.abs(e.gameSecond() - seconds) <= properties.getGoldMatchSeconds()
          && (best == null
              || Math.abs(e.gameSecond() - seconds) < Math.abs(best.gameSecond() - seconds))) {
        best = e;
      }
    }
    if (best == null) {
      return null;
    }
    paid.add(best);
    return best.value();
  }

  /**
   * In the mid lane zone at the pickup (by his activity segment when there is no position), or the
   * pickup is early and mid is his main lane of the first minutes.
   */
  private boolean midLane(long slot, int seconds, Zone zone) {
    List<ActivitySegment> own = segments.getOrDefault(slot, List.of());
    boolean inMid =
        zone != null
            ? zone.kind() == Zone.Kind.LANE && zone.lane() == Zone.Lane.MID
            : own.stream()
                .anyMatch(
                    s ->
                        s.lane() == Zone.Lane.MID
                            && s.startSeconds() <= seconds
                            && seconds < s.endSeconds());
    return inMid
        || (seconds < properties.getMidLanePickupSeconds() && mainEarlyLane(own) == Zone.Lane.MID);
  }

  /**
   * When the rune's modifier ended on the hero: its removal, or his death if sooner. Null for a
   * rune with no modifier, or when no modifier of that rune began near the pickup (a blob stored
   * before the modifiers were kept), so the reader falls back on the duration property.
   */
  private Double effectEnd(long slot, int seconds, RuneType type) {
    String hero = heroOfSlot.get(slot);
    if (type.modifier() == null || hero == null) {
      return null;
    }
    CombatEvent begin = null;
    for (CombatEvent e : events) {
      if (e.kind() == CombatEventKind.MODIFIER_ADD
          && e.target().equals(hero)
          && e.inflictor().equals(type.modifier())
          && Math.abs(e.gameSecond() - seconds) <= properties.getModifierMatchSeconds()
          && (begin == null
              || Math.abs(e.gameSecond() - seconds) < Math.abs(begin.gameSecond() - seconds))) {
        begin = e;
      }
    }
    if (begin == null) {
      return null;
    }
    for (CombatEvent e : events) {
      boolean ends =
          e.gameSecond() > begin.gameSecond()
              && e.target().equals(hero)
              && ((e.kind() == CombatEventKind.MODIFIER_REMOVE
                      && e.inflictor().equals(type.modifier()))
                  || (e.kind() == CombatEventKind.DEATH && e.targetIsRealHero()));
      if (ends) {
        return e.gameSecond();
      }
    }
    return null;
  }

  private Zone.Lane mainEarlyLane(List<ActivitySegment> own) {
    Map<Zone.Lane, Integer> seconds = new EnumMap<>(Zone.Lane.class);
    for (ActivitySegment s : own) {
      if (s.lane() == null) {
        continue;
      }
      int overlap =
          Math.min(s.endSeconds(), properties.getMidLaneEarlySeconds())
              - Math.max(s.startSeconds(), 0);
      if (overlap > 0) {
        seconds.merge(s.lane(), overlap, Integer::sum);
      }
    }
    return seconds.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
  }
}
