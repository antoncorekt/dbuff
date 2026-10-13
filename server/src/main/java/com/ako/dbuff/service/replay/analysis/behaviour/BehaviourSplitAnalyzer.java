package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.TeamSplitStretchRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code team_split_stretch} row for each stretch in which a team kept one posture
 * (grouped, split or mixed) from the end of the last lane pair on, with the towers and the hero
 * deaths of the stretch. Reads tracks, activity, lane results, deaths and building kills.
 */
@Slf4j
@Order(91)
@Component
@RequiredArgsConstructor
public class BehaviourSplitAnalyzer implements ReplayAnalyzer {
  private final MatchMapLayout matchMapLayout;
  private final SlotTraces slotTraces;
  private final StoredActivity activity;
  private final StoredEventTimes eventTimes;
  private final LaneResultRepo laneResultRepo;
  private final BuildingKillContextRepo buildingRepo;
  private final BehaviourProperties properties;
  private final TeamSplitStretchRepo splitRepo;

  @Override
  public String name() {
    return "team-splits";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "activity", "deaths", "lanes", "building-kills");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info(
          "Match {} was not played on the frozen map layout, no split stretches stored", matchId);
      return;
    }
    Map<Long, Trace> traces = slotTraces.of(matchId);
    if (traces.isEmpty()) {
      return;
    }
    int from = startSeconds(laneResultRepo.findByMatchId(matchId), properties);
    List<TeamSplit> splits =
        new SplitFinder(properties, layout::zone)
            .find(new SplitFinder.Inputs(traces, activity.bySlot(matchId), from, events(matchId)));
    splitRepo.saveAll(splits.stream().map(s -> row(matchId, s)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    splitRepo.deleteByMatchId(matchId);
  }

  /** The end of the latest lane pair, or the fallback when no lane result has an end. */
  static int startSeconds(List<LaneResultDomain> results, BehaviourProperties properties) {
    return results.stream()
        .map(LaneResultDomain::getEndSeconds)
        .filter(Objects::nonNull)
        .max(Integer::compare)
        .orElse(properties.getSplitFallbackStartSeconds());
  }

  private Map<TeamSide, TeamEvents> events(long matchId) {
    List<TowerFall> falls = TowerFall.of(buildingRepo.findByMatchId(matchId));
    Map<TeamSide, List<Double>> deaths = deathsByTeam(eventTimes.deaths(matchId));
    Map<TeamSide, TeamEvents> events = new EnumMap<>(TeamSide.class);
    for (TeamSide team : TeamSide.values()) {
      events.put(
          team,
          new TeamEvents(
              towerTimes(falls, f -> f.owner() == team),
              towerTimes(falls, f -> f.owner() != team && !f.denied()),
              deaths.get(team),
              deaths.get(team.opponent())));
    }
    return events;
  }

  private static List<Double> towerTimes(List<TowerFall> falls, Predicate<TowerFall> which) {
    return falls.stream().filter(which).map(TowerFall::seconds).toList();
  }

  private static Map<TeamSide, List<Double>> deathsByTeam(Map<Long, List<Double>> bySlot) {
    Map<TeamSide, List<Double>> byTeam = new EnumMap<>(TeamSide.class);
    for (TeamSide team : TeamSide.values()) {
      byTeam.put(team, new ArrayList<>());
    }
    bySlot.forEach((slot, times) -> byTeam.get(TeamSide.ofSlot(slot)).addAll(times));
    return byTeam;
  }

  private TeamSplitStretchDomain row(long matchId, TeamSplit s) {
    return TeamSplitStretchDomain.builder()
        .matchId(matchId)
        .team(s.team().name())
        .startSeconds(s.stretch().startSeconds())
        .endSeconds(s.stretch().endSeconds())
        .label(s.stretch().posture().name())
        .towersLost(s.towersLost())
        .towersTaken(s.towersTaken())
        .ownHeroesDied(s.ownHeroesDied())
        .enemyHeroesDied(s.enemyHeroesDied())
        .analysisVersion(version())
        .build();
  }
}
