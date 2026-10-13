package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns the stored facts of a match into each team's group-or-split stretches. */
public final class SplitFinder {
  /**
   * @param fromSeconds the first second read: the end of the latest lane pair, or the fallback
   * @param events what happened to each team
   */
  public record Inputs(
      Map<Long, Trace> traces,
      Map<Long, List<ActivitySegment>> activity,
      int fromSeconds,
      Map<TeamSide, TeamEvents> events) {}

  private final BehaviourProperties properties;
  private final ZoneLookup zones;

  public SplitFinder(BehaviourProperties properties, ZoneLookup zones) {
    this.properties = properties;
    this.zones = zones;
  }

  public List<TeamSplit> find(Inputs in) {
    TeamClusters clusters =
        new TeamClusters(properties.getClusterDistance(), properties.getMinClusterSize(), zones);
    PostureMinutes minutes =
        new PostureMinutes(
            new PostureClassifier(
                clusters,
                properties.getClusterDistance(),
                properties.getGroupedMinHeroes(),
                properties.getSplitMinAloneHeroes()));
    SplitStretches stretches = new SplitStretches(properties.getMinStretchMinutes());
    List<TeamSplit> splits = new ArrayList<>();
    for (TeamSide team : TeamSide.values()) {
      Map<Long, Trace> traces =
          in.traces().entrySet().stream()
              .filter(e -> TeamSide.ofSlot(e.getKey()) == team)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      TeamEvents events = in.events().get(team);
      for (Stretch stretch : stretches.of(minutes.of(traces, in.activity(), in.fromSeconds()))) {
        splits.add(
            new TeamSplit(
                team,
                stretch,
                count(events.towersLost(), stretch),
                count(events.towersTaken(), stretch),
                count(events.heroesLost(), stretch),
                count(events.kills(), stretch)));
      }
    }
    return splits;
  }

  private static int count(List<Double> times, Stretch stretch) {
    return (int) times.stream().filter(stretch::contains).count();
  }
}
