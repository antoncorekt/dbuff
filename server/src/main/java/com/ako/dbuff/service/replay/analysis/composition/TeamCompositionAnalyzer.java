package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.LanePosition;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges the composition of each team from the player profiles and roles stored by {@code
 * PlayerRoleAnalyzer}: the PICK stage from the INTENT profiles and the FINAL stage from the FINAL
 * ones, each only when the team has all five. The rules come from the {@link CompositionRules} of
 * the match's mode.
 */
@Slf4j
@Order(16)
@Component
@RequiredArgsConstructor
public class TeamCompositionAnalyzer implements ReplayAnalyzer {
  private static final int TEAM_SIZE = 5;

  private final List<CompositionRules> rules;
  private final StoredMatchModes modes;
  private final PlayerRoleProfileRepo profileRepo;
  private final PlayerRoleRepo roleRepo;
  private final TeamCompositionRepo compositionRepo;

  @Override
  public String name() {
    return "composition";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return rules.stream().anyMatch(r -> r.supports(mode));
  }

  @Override
  public Set<String> requires() {
    return Set.of("roles");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    CompositionRules modeRules =
        mode == null ? null : rules.stream().filter(r -> r.supports(mode)).findFirst().orElse(null);
    if (modeRules == null) {
      return;
    }
    Map<Long, Integer> finalRoles =
        roleRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getRole() != null)
            .collect(Collectors.toMap(PlayerRoleDomain::getPlayerSlot, PlayerRoleDomain::getRole));
    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(matchId);
    List<TeamCompositionDomain> rows = new ArrayList<>();
    for (TeamSide side : TeamSide.values()) {
      Map<Long, Integer> intentRoles = rolesOf(profiles, side, RoleStage.INTENT);
      for (CompositionStage stage : CompositionStage.values()) {
        List<PlayerRoleProfileDomain> team = teamRows(profiles, side, stage.profileStage());
        if (team.isEmpty()) {
          continue;
        }
        if (team.size() != TEAM_SIZE) {
          log.info(
              "Match {}: {} has {} {} profiles, no composition for it",
              matchId,
              side,
              team.size(),
              stage.profileStage());
          continue;
        }
        boolean withIntent = stage == CompositionStage.FINAL && intentRoles.size() == TEAM_SIZE;
        List<TeamMember> members =
            team.stream().map(p -> member(p, finalRoles, withIntent ? intentRoles : null)).toList();
        CompositionVerdict verdict = modeRules.evaluate(stage, members);
        rows.add(
            TeamCompositionDomain.builder()
                .matchId(matchId)
                .team(side.name())
                .stage(stage.name())
                .tokens(String.join(",", verdict.tokens()))
                .summary(verdict.summary())
                .analysisVersion(version())
                .build());
      }
    }
    compositionRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    compositionRepo.deleteByMatchId(matchId);
  }

  private static TeamMember member(
      PlayerRoleProfileDomain p, Map<Long, Integer> finalRoles, Map<Long, Integer> intentRoles) {
    boolean isFinal = RoleStage.FINAL.name().equals(p.getStage());
    int role = isFinal ? finalRoles.getOrDefault(p.getPlayerSlot(), p.getRole()) : p.getRole();
    return new TeamMember(
        p.getPlayerSlot(),
        role,
        intentRoles == null ? null : intentRoles.get(p.getPlayerSlot()),
        profile(p));
  }

  private static FunctionProfile profile(PlayerRoleProfileDomain p) {
    return new FunctionProfile(
        p.getLane() == null ? null : LanePosition.valueOf(p.getLane()),
        p.getFarmPriority(),
        p.getJungle(),
        p.getRoaming(),
        p.getVisionDuty(),
        p.getInitiation(),
        p.getDisable(),
        p.getSaveHeal(),
        p.getDamage(),
        p.getDurability());
  }

  private static List<PlayerRoleProfileDomain> teamRows(
      List<PlayerRoleProfileDomain> profiles, TeamSide side, RoleStage stage) {
    return profiles.stream()
        .filter(p -> stage.name().equals(p.getStage()) && p.getRole() != null)
        .filter(p -> TeamSide.ofSlot(p.getPlayerSlot()) == side)
        .sorted(Comparator.comparing(PlayerRoleProfileDomain::getPlayerSlot))
        .toList();
  }

  private static Map<Long, Integer> rolesOf(
      List<PlayerRoleProfileDomain> profiles, TeamSide side, RoleStage stage) {
    Map<Long, Integer> roles = new HashMap<>();
    teamRows(profiles, side, stage).forEach(p -> roles.put(p.getPlayerSlot(), p.getRole()));
    return roles;
  }
}
