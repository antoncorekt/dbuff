package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The important fights with a winner, with the winner's state before each. */
final class ObjectiveFights {
  private ObjectiveFights() {}

  static List<ObjectiveFight> of(
      List<FightImportanceDomain> importance,
      List<FightDomain> fights,
      List<GameStateSegmentDomain> segments) {
    Map<Integer, FightDomain> byIndex =
        fights.stream()
            .filter(f -> f.getFightIndex() != null && f.getStartSeconds() != null)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    return importance.stream()
        .filter(i -> i.isImportant() && byIndex.containsKey(i.getFightIndex()))
        .filter(i -> Enums.find(TeamSide.class, i.getWinningTeam()) != null)
        .sorted(Comparator.comparing(FightImportanceDomain::getFightIndex))
        .map(
            i -> {
              FightDomain f = byIndex.get(i.getFightIndex());
              TeamSide winner = Enums.find(TeamSide.class, i.getWinningTeam());
              double end = f.getEndSeconds() == null ? f.getStartSeconds() : f.getEndSeconds();
              return new ObjectiveFight(
                  i.getFightIndex(),
                  winner,
                  f.getStartSeconds(),
                  end,
                  stateAt(segments, winner, f.getStartSeconds()));
            })
        .toList();
  }

  /** The state of the segment the time falls in, the latest one that started when two touch. */
  private static State stateAt(List<GameStateSegmentDomain> segments, TeamSide team, double time) {
    return segments.stream()
        .filter(s -> team.name().equals(s.getTeam()))
        .filter(s -> s.getStartSeconds() != null && s.getStartSeconds() <= time)
        .filter(s -> s.getEndSeconds() == null || time <= s.getEndSeconds())
        .max(Comparator.comparingInt(GameStateSegmentDomain::getStartSeconds))
        .map(s -> Enums.find(State.class, s.getState()))
        .orElse(null);
  }
}
