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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the stored rune events into pickups: position, bounty gold and the mid lane flag. */
final class RunePickups {
  private final RuneProperties properties;
  private final Map<Long, String> heroOfSlot;
  private final Map<Long, ReplayPlayerTrackDomain> tracks;
  private final Map<Long, List<ActivitySegment>> segments;
  private final MapLayout layout;
  private final List<CombatEvent> bountyGold;
  private final Set<CombatEvent> paid = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * @param heroOfSlot the combat-log hero name of each player slot
   * @param layout null when the map version is not frozen
   * @param bountyGold the bounty-rune gold entries; empty when the log keeps no gold reasons
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
    List<RunePickupDomain> rows = new ArrayList<>();
    for (RuneEventDomain rune : inOrder) {
      long slot = rune.getPlayerSlot();
      int seconds = rune.getEventTime().intValue();
      RuneType type = RuneType.of(rune.getRuneType());
      Zone zone = zoneAt(slot, seconds);
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
              .gold(type == RuneType.BOUNTY ? goldAt(slot, seconds) : null)
              .midLane(midLane(slot, seconds))
              .analysisVersion(version)
              .build());
    }
    return rows;
  }

  private Zone zoneAt(long slot, int seconds) {
    ReplayPlayerTrackDomain track = tracks.get(slot);
    if (layout == null || track == null || track.getSampleCount() == null) {
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

  /** In the mid lane now by the activity segment, or mid as the main lane of the early game. */
  private boolean midLane(long slot, int seconds) {
    List<ActivitySegment> own = segments.getOrDefault(slot, List.of());
    boolean now =
        own.stream()
            .anyMatch(
                s ->
                    s.lane() == Zone.Lane.MID
                        && s.startSeconds() <= seconds
                        && seconds < s.endSeconds());
    return now || mainEarlyLane(own) == Zone.Lane.MID;
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
