package com.ako.dbuff.service.replay.analysis.role;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns function profiles into roles: scores each profile against each role, then gives the five
 * players of a team five different roles at the best total with {@link RoleAssignmentSolver}. An
 * unknown function counts as a neutral value and lowers the confidence.
 */
@Component
@RequiredArgsConstructor
public class RoleFitter {
  public static final int ROLES = 5;

  private final RoleProfileProperties properties;

  /**
   * @param role 1 to 5
   * @param confidence 0 to 1: how much the team total drops if the player takes another role, times
   *     the share of the functions that were known
   * @param scores the score of the player in each role, index 0 for role 1
   */
  public record RoleFit(int role, double confidence, double[] scores) {}

  public double[] scores(FunctionProfile profile) {
    RoleProfileProperties.Fit fit = properties.getFit();
    double[] scores = new double[ROLES];
    for (int role = 1; role <= ROLES; role++) {
      double sum = 0;
      for (RoleFunction function : RoleFunction.values()) {
        Double value = profile.of(function);
        sum +=
            fit.getWeights().get(role).get(function)
                * (value == null ? fit.getUnknownValue() : value);
      }
      if (profile.lane() != null) {
        sum += fit.getLaneBonus().get(role).get(profile.lane());
      }
      scores[role - 1] = sum;
    }
    return scores;
  }

  /** The role with the highest score, the first in order on a tie. */
  public static int bestRole(double[] scores) {
    int best = 0;
    for (int i = 1; i < scores.length; i++) {
      if (scores[i] > scores[best]) {
        best = i;
      }
    }
    return best + 1;
  }

  /**
   * @param team the profiles of one team
   * @return one fit per profile in the same order; empty unless the team has one profile per role
   */
  public List<RoleFit> fit(List<FunctionProfile> team) {
    if (team.size() != ROLES) {
      return List.of();
    }
    double[][] score = team.stream().map(this::scores).toArray(double[][]::new);
    RoleAssignmentSolver.Solution solution = RoleAssignmentSolver.solve(score);
    List<RoleFit> result = new ArrayList<>();
    for (int i = 0; i < ROLES; i++) {
      double confidence =
          Math.min(1, solution.margin()[i] / properties.getFit().getFullConfidenceMargin())
              * knownShare(team.get(i));
      result.add(
          new RoleFit(solution.roleOf()[i] + 1, Math.round(confidence * 100) / 100.0, score[i]));
    }
    return result;
  }

  private static double knownShare(FunctionProfile profile) {
    long known = 0;
    for (RoleFunction function : RoleFunction.values()) {
      known += profile.of(function) == null ? 0 : 1;
    }
    return known / (double) RoleFunction.values().length;
  }
}
