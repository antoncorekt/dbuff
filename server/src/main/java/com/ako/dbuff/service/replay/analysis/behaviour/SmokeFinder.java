package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns the smoke uses and modifiers of a match into the smoke uses it keeps. */
final class SmokeFinder {
  /**
   * A hero who spawns at the use is read one track sample later, one who dies at the end, earlier.
   */
  private static final int SAMPLE_SECONDS = 5;

  /**
   * @param dropped uses without the modifier on the user within the group window of the use
   */
  record Found(List<SmokeUse> uses, int dropped) {}

  private final BehaviourProperties properties;
  private final ZoneLookup zones;

  SmokeFinder(BehaviourProperties properties, ZoneLookup zones) {
    this.properties = properties;
    this.zones = zones;
  }

  Found find(
      Map<Long, Trace> traces,
      List<SmokeEvents.Use> uses,
      List<SmokeHold> holds,
      SmokeOutcomes.Events events) {
    List<SmokeUse> found = new ArrayList<>();
    int dropped = 0;
    for (SmokeEvents.Use use :
        uses.stream().sorted(Comparator.comparingDouble(SmokeEvents.Use::seconds)).toList()) {
      Optional<SmokeUse> smoke = smoke(use, traces, holds, events);
      if (smoke.isEmpty()) {
        dropped++;
      } else if (found.stream().noneMatch(f -> sameSmoke(f, smoke.get()))) {
        found.add(smoke.get());
      }
    }
    return new Found(found, dropped);
  }

  /** Two uses in a row by one group (a stack used twice) are one smoke. */
  private boolean sameSmoke(SmokeUse a, SmokeUse b) {
    return a.team() == b.team()
        && a.group().equals(b.group())
        && Math.abs(a.startSeconds() - b.startSeconds()) <= properties.getSmokeGroupSeconds();
  }

  private Optional<SmokeUse> smoke(
      SmokeEvents.Use use,
      Map<Long, Trace> traces,
      List<SmokeHold> holds,
      SmokeOutcomes.Events events) {
    TeamSide team = TeamSide.ofSlot(use.userSlot());
    List<SmokeHold> group =
        holds.stream()
            .filter(h -> TeamSide.ofSlot(h.slot()) == team)
            .filter(h -> h.sourceSlot() == null || h.sourceSlot() == use.userSlot())
            .filter(
                h -> Math.abs(h.addSeconds() - use.seconds()) <= properties.getSmokeGroupSeconds())
            .sorted(Comparator.comparingLong(SmokeHold::slot))
            .toList();
    if (group.stream().noneMatch(h -> h.slot() == use.userSlot())) {
      return Optional.empty();
    }
    double slack = properties.getSmokeExpirySlackSeconds();
    List<SmokeHold> broken =
        group.stream().filter(h -> h.state(slack) == SmokeHold.State.BROKEN).toList();
    boolean unknown = group.stream().anyMatch(h -> h.state(slack) == SmokeHold.State.UNKNOWN);
    double end =
        broken.isEmpty()
            ? group.stream().mapToDouble(SmokeHold::endSeconds).max().orElseThrow()
            : broken.stream().mapToDouble(SmokeHold::endSeconds).min().orElseThrow();
    SmokeUse.End reason =
        !broken.isEmpty()
            ? SmokeUse.End.BROKEN
            : unknown ? SmokeUse.End.UNKNOWN : SmokeUse.End.EXPIRED;
    List<Long> slots = group.stream().map(SmokeHold::slot).toList();
    SmokeResult result =
        SmokeOutcomes.of(
            team, slots, use.seconds(), end + properties.getSmokeOutcomeSeconds(), events);
    return Optional.of(
        new SmokeUse(
            team,
            use.userSlot(),
            slots,
            (int) Math.round(use.seconds()),
            (int) Math.round(end),
            reason,
            zoneAt(traces, slots, use.seconds(), use.seconds() + SAMPLE_SECONDS),
            zoneAt(traces, slots, end, end - SAMPLE_SECONDS),
            result));
  }

  /** The zone of the group's middle at the first of the times at which a hero of it is alive. */
  private Zone zoneAt(Map<Long, Trace> traces, List<Long> slots, double... times) {
    for (double time : times) {
      Map<Long, Point> alive = TraceSamples.alive(traces, (int) Math.round(time));
      List<Long> present = slots.stream().filter(alive::containsKey).toList();
      if (!present.isEmpty()) {
        Point centre = TeamClusters.centre(present, alive);
        return zones.at(centre.x(), centre.y());
      }
    }
    return null;
  }
}
