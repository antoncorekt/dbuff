package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Labels each whole game minute of one team: every track sample of the minute is classified, and
 * the label seen most often wins, a tie being MIXED. A minute with no sample of a team that has two
 * living heroes, or one the tracks do not cover completely, gets no label.
 */
public final class PostureMinutes {
  private static final int MINUTE = 60;

  private final PostureClassifier classifier;

  public PostureMinutes(PostureClassifier classifier) {
    this.classifier = classifier;
  }

  /**
   * @param traces the team's samples by player slot
   * @param activity the stored activity segments by slot
   * @param fromSeconds the first minute read is the one that starts at or after this
   */
  public List<MinutePosture> of(
      Map<Long, Trace> traces, Map<Long, List<ActivitySegment>> activity, int fromSeconds) {
    if (traces.isEmpty()) {
      return List.of();
    }
    int step = traces.values().iterator().next().stepSeconds();
    int end =
        traces.values().stream()
                .mapToInt(t -> t.startSeconds() + (t.x().length - 1) * t.stepSeconds())
                .max()
                .orElse(0)
            + step;
    List<MinutePosture> minutes = new ArrayList<>();
    for (int minute = (fromSeconds + MINUTE - 1) / MINUTE; (minute + 1) * MINUTE <= end; minute++) {
      Map<Posture, Integer> seen = new EnumMap<>(Posture.class);
      for (int t = minute * MINUTE; t < (minute + 1) * MINUTE; t += step) {
        Map<Long, Point> alive = TraceSamples.alive(traces, t);
        int time = t;
        Set<Long> jungle =
            alive.keySet().stream()
                .filter(s -> TraceSamples.farmingJungle(activity.get(s), time))
                .collect(Collectors.toSet());
        classifier.classify(alive, jungle).ifPresent(p -> seen.merge(p, 1, Integer::sum));
      }
      int labelled = minute;
      winner(seen).ifPresent(p -> minutes.add(new MinutePosture(labelled, p)));
    }
    return minutes;
  }

  private static Optional<Posture> winner(Map<Posture, Integer> seen) {
    int best = seen.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    List<Posture> top =
        seen.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
    if (top.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(top.size() == 1 ? top.getFirst() : Posture.MIXED);
  }
}
