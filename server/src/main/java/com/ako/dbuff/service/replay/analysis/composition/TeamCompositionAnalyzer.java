package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
  private static final String ELLIPSIS = "...";

  private final List<CompositionRules> rules;
  private final StoredMatchModes modes;
  private final PlayerRoleProfileRepo profileRepo;
  private final TeamCompositionRepo compositionRepo;

  @Override
  public String name() {
    return "composition";
  }

  @Override
  public int version() {
    return 2;
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
    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(matchId);
    List<TeamCompositionDomain> rows = new ArrayList<>();
    for (TeamSide side : TeamSide.values()) {
      Map<RoleStage, List<PlayerRoleProfileDomain>> byStage = new EnumMap<>(RoleStage.class);
      for (RoleStage profileStage : RoleStage.values()) {
        byStage.put(profileStage, teamRows(matchId, profiles, side, profileStage));
      }
      Map<Long, Integer> intentRoles = rolesOf(byStage.get(RoleStage.INTENT));
      for (CompositionStage stage : CompositionStage.values()) {
        List<PlayerRoleProfileDomain> team = byStage.get(stage.profileStage());
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
            team.stream().map(p -> member(p, withIntent ? intentRoles : null)).toList();
        CompositionVerdict verdict = modeRules.evaluate(stage, members);
        rows.add(
            TeamCompositionDomain.builder()
                .matchId(matchId)
                .team(side.name())
                .stage(stage.name())
                .tokens(
                    fit(
                        String.join(",", verdict.tokens()),
                        TeamCompositionDomain.TOKENS_LENGTH,
                        ","))
                .summary(fit(verdict.summary(), TeamCompositionDomain.SUMMARY_LENGTH, ""))
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

  /**
   * The text cut to {@code max} characters: at the last {@code separator} for a list, so no half
   * token is stored, or with an ellipsis for a sentence (empty separator).
   */
  static String fit(String text, int max, String separator) {
    if (text.length() <= max) {
      return text;
    }
    if (separator.isEmpty()) {
      return text.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
    int cut = text.lastIndexOf(separator, max);
    return text.substring(0, cut < 0 ? max : cut);
  }

  private static TeamMember member(PlayerRoleProfileDomain p, Map<Long, Integer> intentRoles) {
    return new TeamMember(
        p.getPlayerSlot(),
        p.getRole(),
        intentRoles == null ? null : intentRoles.get(p.getPlayerSlot()),
        FunctionProfile.stored(p));
  }

  /**
   * The rows of one team and stage, one per slot (the last stored wins); rows without a slot or a
   * role are left out. One line says what was dropped.
   */
  private static List<PlayerRoleProfileDomain> teamRows(
      long matchId, List<PlayerRoleProfileDomain> profiles, TeamSide side, RoleStage stage) {
    Map<Long, PlayerRoleProfileDomain> bySlot = new TreeMap<>();
    int unusable = 0;
    int repeated = 0;
    for (PlayerRoleProfileDomain p : profiles) {
      if (!stage.name().equals(p.getStage())
          || (p.getPlayerSlot() != null && TeamSide.ofSlot(p.getPlayerSlot()) != side)) {
        continue;
      }
      if (p.getPlayerSlot() == null || p.getRole() == null) {
        unusable++;
      } else if (bySlot.put(p.getPlayerSlot(), p) != null) {
        repeated++;
      }
    }
    if (unusable + repeated > 0) {
      log.info(
          "Match {}: {} {} profiles: {} without a slot or role left out, {} repeated slots merged",
          matchId,
          side,
          stage,
          unusable,
          repeated);
    }
    return List.copyOf(bySlot.values());
  }

  private static Map<Long, Integer> rolesOf(List<PlayerRoleProfileDomain> rows) {
    return rows.stream()
        .collect(
            Collectors.toMap(
                PlayerRoleProfileDomain::getPlayerSlot, PlayerRoleProfileDomain::getRole));
  }
}
