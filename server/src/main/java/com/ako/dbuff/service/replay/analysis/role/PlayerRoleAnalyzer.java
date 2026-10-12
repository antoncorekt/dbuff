package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the role (1 carry to 5 hard support) of each player, resolved per game mode by a {@link
 * RoleResolver}. It reads OpenDota's statistics and net worth and the activity stored before it, so
 * it runs after the activity and before the analyzers that will use roles. A mode without a
 * resolver gets no roles.
 */
@Slf4j
@Order(15)
@Component
@RequiredArgsConstructor
public class PlayerRoleAnalyzer implements ReplayAnalyzer {
  private static final long FIRST_DIRE_SLOT = 128;
  private static final int TEAM_SIZE = 5;

  private final List<RoleResolver> resolvers;
  private final StoredMatchModes modes;
  private final PlayerGameStatisticRepo statisticRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final StoredActivity storedActivity;
  private final PlayerRoleRepo roleRepo;

  @Override
  public String name() {
    return "roles";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return resolvers.stream().anyMatch(r -> r.supports(mode));
  }

  @Override
  public Set<String> requires() {
    return Set.of("activity");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    RoleResolver resolver =
        mode == null
            ? null
            : resolvers.stream().filter(r -> r.supports(mode)).findFirst().orElse(null);
    if (resolver == null) {
      return;
    }
    Map<Long, List<ActivitySegment>> activity = storedActivity.bySlot(matchId);
    List<PlayerMatchStatisticDomain> stats = statisticRepo.findAllByMatchId(matchId);
    Map<Long, double[]> series = netWorthSeries(matchId, stats);
    List<RolePlayer> players =
        stats.stream()
            .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .map(
                s ->
                    new RolePlayer(
                        s.getPlayerSlot(),
                        s.getLaneRole(),
                        s.getNetWorth(),
                        series.get(s.getPlayerSlot()),
                        activity.getOrDefault(s.getPlayerSlot(), List.of())))
            .toList();
    List<PlayerRoleDomain> rows = new ArrayList<>();
    for (boolean dire : new boolean[] {false, true}) {
      List<RolePlayer> team =
          players.stream().filter(p -> (p.slot() >= FIRST_DIRE_SLOT) == dire).toList();
      if (!isTeam(team, dire)) {
        log.info(
            "Match {} has stored slots {} on the {} team, no roles for it",
            matchId,
            team.stream().map(RolePlayer::slot).toList(),
            dire ? "dire" : "radiant");
        continue;
      }
      resolver.resolve(team).forEach(a -> rows.add(row(matchId, a)));
    }
    roleRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    roleRepo.deleteByMatchId(matchId);
  }

  private PlayerRoleDomain row(long matchId, RoleAssignment assignment) {
    return PlayerRoleDomain.builder()
        .matchId(matchId)
        .playerSlot(assignment.slot())
        .role(assignment.role())
        .source(assignment.source().name())
        .confidence(assignment.confidence())
        .evidence(assignment.evidence())
        .analysisVersion(version())
        .build();
  }

  private static boolean isTeam(List<RolePlayer> team, boolean dire) {
    long first = dire ? FIRST_DIRE_SLOT : 0;
    return team.size() == TEAM_SIZE
        && team.stream()
            .map(RolePlayer::slot)
            .collect(Collectors.toSet())
            .equals(LongStream.range(first, first + TEAM_SIZE).boxed().collect(Collectors.toSet()));
  }

  /**
   * Net worth by minute for each slot with any stored; a minute without a value takes the latest
   * one before it, and NaN stands before the first. One line says what was missing or filled.
   */
  private Map<Long, double[]> netWorthSeries(long matchId, List<PlayerMatchStatisticDomain> stats) {
    Map<Long, Map<Integer, Long>> worth = new HashMap<>();
    Map<Long, Integer> lastMinute = new HashMap<>();
    for (PlayerTimelineDomain row : timelineRepo.findByMatchId(matchId)) {
      if (row.getGameMinute() == null || row.getPlayerSlot() == null) {
        continue;
      }
      lastMinute.merge(row.getPlayerSlot(), row.getGameMinute(), Math::max);
      if (row.getNetworth() != null) {
        worth
            .computeIfAbsent(row.getPlayerSlot(), s -> new HashMap<>())
            .put(row.getGameMinute(), row.getNetworth());
      }
    }
    Map<Long, double[]> series = new HashMap<>();
    int filled = 0;
    for (Map.Entry<Long, Map<Integer, Long>> e : worth.entrySet()) {
      double[] minutes = new double[lastMinute.get(e.getKey()) + 1];
      double latest = Double.NaN;
      boolean gap = false;
      for (int minute = 0; minute < minutes.length; minute++) {
        Long value = e.getValue().get(minute);
        if (value != null) {
          latest = value;
        } else {
          gap = true;
        }
        minutes[minute] = latest;
      }
      filled += gap ? 1 : 0;
      series.put(e.getKey(), minutes);
    }
    long without = stats.stream().filter(s -> !series.containsKey(s.getPlayerSlot())).count();
    if (without > 0 || filled > 0) {
      log.info(
          "Match {}: net worth series missing for {} players and filled for {}",
          matchId,
          without,
          filled);
    }
    return series;
  }
}
