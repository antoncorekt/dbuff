package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * In All Pick a hero is picked for a role, so the roles are read from the match: the lane each
 * player was in (OpenDota's lane role and where the replay shows him), what he did in the lane
 * (mid, jungle, roaming) and how his net worth ranks in his team, final and at the end of the
 * laning phase. The five players are given five different roles at the best total fit.
 */
@Component
@RequiredArgsConstructor
public class AllPickRoleResolver implements RoleResolver {
  private static final int ROLES = 5;
  private static final int TEAM_SIZE = 5;
  private static final long SAFE_LANE = 1;
  private static final long MID_LANE = 2;
  private static final long OFF_LANE = 3;
  private static final long JUNGLE = 4;
  private static final double UNKNOWN_RANK = 3;
  private static final double FARM_WEIGHT = 0.6;
  private static final double MID_SHARE = 0.5;
  private static final double JUNGLE_SHARE = 0.3;
  private static final double ROAM_SHARE = 0.5;
  private static final double FULL_CONFIDENCE_MARGIN = 2.0;

  /** Added to a role's score when the signal is there; rows are roles 1 to 5. */
  private static final double[] MID = {-0.5, 3, -0.5, 0.5, -0.5};

  private static final double[] SAFE = {2, -1.5, -1, 0, 1.5};
  private static final double[] OFF = {-1, -1.5, 2, 1.5, 0};
  private static final double[] JUNGLE_BONUS = {0, 0, 0.5, 2, -0.5};
  private static final double[] ROAM = {-1, -1, -0.5, 1.5, 0};

  private final ReplayAnalysisProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == AllPickProfile.MODE_ID;
  }

  @Override
  public List<RoleAssignment> resolve(List<RolePlayer> team) {
    if (team.size() != TEAM_SIZE) {
      return List.of();
    }
    int laningMinute = properties.getPhaseLaneEndMinutes();
    List<LaningActivity> activity =
        team.stream().map(p -> LaningActivity.of(p.activity(), laningMinute * 60)).toList();
    double[] finalRank = ranks(team.stream().map(RolePlayer::finalWorth).toList());
    double[] laningRank = ranks(team.stream().map(p -> p.worthAt(laningMinute)).toList());
    double[][] score = new double[TEAM_SIZE][ROLES];
    double[] farmRank = new double[TEAM_SIZE];
    for (int i = 0; i < TEAM_SIZE; i++) {
      farmRank[i] = (finalRank[i] + laningRank[i]) / 2;
      for (int role = 0; role < ROLES; role++) {
        score[i][role] =
            -FARM_WEIGHT * Math.abs(farmRank[i] - (role + 1))
                + laneBonus(team.get(i), activity.get(i), role);
      }
    }
    RoleAssignmentSolver.Solution solution = RoleAssignmentSolver.solve(score);
    List<RoleAssignment> result = new ArrayList<>();
    for (int i = 0; i < TEAM_SIZE; i++) {
      result.add(
          new RoleAssignment(
              team.get(i).slot(),
              solution.roleOf()[i] + 1,
              PlayerRoleSource.DERIVED,
              Math.round(Math.min(1, solution.margin()[i] / FULL_CONFIDENCE_MARGIN) * 100) / 100.0,
              evidence(team.get(i), activity.get(i), finalRank[i], laningRank[i])));
    }
    return result;
  }

  private static double laneBonus(RolePlayer player, LaningActivity activity, int role) {
    Long laneRole = player.laneRole();
    double bonus = 0;
    if (laneRole != null && laneRole == MID_LANE || activity.midShare() >= MID_SHARE) {
      bonus += MID[role];
    }
    if (laneRole != null && laneRole == SAFE_LANE) {
      bonus += SAFE[role];
    }
    if (laneRole != null && laneRole == OFF_LANE) {
      bonus += OFF[role];
    }
    if (laneRole != null && laneRole == JUNGLE || activity.jungleShare() >= JUNGLE_SHARE) {
      bonus += JUNGLE_BONUS[role];
    }
    if (activity.roamShare() >= ROAM_SHARE) {
      bonus += ROAM[role];
    }
    return bonus;
  }

  /** Place by worth with 1 the highest and a tie sharing the average place; 3 when unknown. */
  private static double[] ranks(List<Double> worth) {
    double[] ranks = new double[worth.size()];
    for (int i = 0; i < ranks.length; i++) {
      if (worth.get(i) == null) {
        ranks[i] = UNKNOWN_RANK;
        continue;
      }
      double own = worth.get(i);
      long higher = worth.stream().filter(w -> w != null && w > own).count();
      long equal = worth.stream().filter(w -> w != null && w == own).count();
      ranks[i] = higher + (equal + 1) / 2.0;
    }
    return ranks;
  }

  private static String evidence(
      RolePlayer player, LaningActivity activity, double finalRank, double laningRank) {
    return String.format(
        Locale.ROOT,
        "lane role %s, net worth place %.1f final and %.1f in the laning phase, laning time mid"
            + " %d%% jungle %d%% other lanes %d%%",
        player.laneRole() == null ? "unknown" : player.laneRole(),
        finalRank,
        laningRank,
        Math.round(activity.midShare() * 100),
        Math.round(activity.jungleShare() * 100),
        Math.round(activity.roamShare() * 100));
  }
}
