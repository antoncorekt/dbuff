package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
  private final PlayerActivitySegmentRepo activityRepo;
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
    return Set.of("players", "tracks", "combat-log");
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
    Map<Long, List<ActivitySegment>> activity = activity(matchId);
    Map<Long, double[]> series = netWorthSeries(matchId);
    List<RolePlayer> players =
        statisticRepo.findAllByMatchId(matchId).stream()
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
      if (team.size() != TEAM_SIZE) {
        log.info("Match {} has {} stored players on a team, no roles for it", matchId, team.size());
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

  private Map<Long, List<ActivitySegment>> activity(long matchId) {
    Map<Long, List<ActivitySegment>> bySlot = new HashMap<>();
    activityRepo
        .findByMatchId(matchId)
        .forEach(
            s ->
                bySlot
                    .computeIfAbsent(s.getPlayerSlot(), k -> new ArrayList<>())
                    .add(ActivitySegment.of(s)));
    return bySlot;
  }

  /** Net worth by minute for each slot that has one stored for every minute from 0. */
  private Map<Long, double[]> netWorthSeries(long matchId) {
    Map<Long, TreeMap<Integer, Long>> bySlot = new HashMap<>();
    for (PlayerTimelineDomain row : timelineRepo.findByMatchId(matchId)) {
      if (row.getNetworth() != null && row.getGameMinute() != null && row.getPlayerSlot() != null) {
        bySlot
            .computeIfAbsent(row.getPlayerSlot(), s -> new TreeMap<>())
            .put(row.getGameMinute(), row.getNetworth());
      }
    }
    Map<Long, double[]> series = new HashMap<>();
    bySlot.forEach(
        (slot, minutes) -> {
          if (minutes.firstKey() == 0 && minutes.lastKey() == minutes.size() - 1) {
            series.put(slot, minutes.values().stream().mapToDouble(Long::doubleValue).toArray());
          }
        });
    return series;
  }
}
