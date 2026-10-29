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
  private static final int MID_ROLE = 2;
  private static final double NOT_OPEN = -1e6;

  private final RoleProfileProperties properties;

  /**
   * @param role 1 to 5
   * @param confidence 0 to 1: how much the team total drops if the player takes another role, times
   *     the share of the functions that were known
   * @param scores the score of the player in each role, index 0 for role 1
   */
  public record RoleFit(int role, double confidence, double[] scores) {}

  /** The scores of a profile read for a stage: the FINAL stage weighs the lane less. */
  public double[] scores(FunctionProfile profile, RoleStage stage) {
    RoleProfileProperties.Fit fit = properties.getFit();
    double unknown = stage == RoleStage.FINAL ? fit.getFinalUnknownValue() : fit.getUnknownValue();
    double laneFactor = stage == RoleStage.FINAL ? fit.getFinalLaneFactor() : 1;
    double[] scores = new double[ROLES];
    for (int role = 1; role <= ROLES; role++) {
      double sum = 0;
      for (RoleFunction function : RoleFunction.scoredFunctions()) {
        Double value = profile.of(function);
        sum += fit.getWeights().get(role).get(function) * (value == null ? unknown : value);
      }
      if (profile.lane() != null) {
        sum += laneFactor * fit.getLaneBonus().get(role).get(profile.lane());
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
   * @param stage how the profiles are scored: the FINAL stage weighs the lane less
   * @return one fit per profile in the same order; empty unless the team has one profile per role
   */
  public List<RoleFit> fit(List<FunctionProfile> team, RoleStage stage) {
    return fit(team, null, stage);
  }

  /**
   * @param players the players of the profiles in the same order, or null; in the FINAL stage their
   *     OpenDota position adds a bonus, and the one mid-laner of a team is fixed to role 2
   */
  public List<RoleFit> fit(List<FunctionProfile> team, List<RolePlayer> players, RoleStage stage) {
    if (team.size() != ROLES) {
      return List.of();
    }
    boolean known = stage == RoleStage.FINAL && players != null && players.size() == ROLES;
    double[][] score = new double[ROLES][];
    for (int i = 0; i < ROLES; i++) {
      score[i] = scores(team.get(i), stage);
      Long position = known ? players.get(i).positionEst() : null;
      if (position != null && position >= 1 && position <= ROLES) {
        score[i][(int) (position - 1)] += properties.getFit().getFinalPositionBonus();
      }
    }
    double[][] solved = known ? withMidLock(score, team, players) : score;
    RoleAssignmentSolver.Solution solution = RoleAssignmentSolver.solve(solved);
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

  private static double[][] withMidLock(
      double[][] score, List<FunctionProfile> team, List<RolePlayer> players) {
    List<Integer> mids = new ArrayList<>();
    for (int i = 0; i < ROLES; i++) {
      Long laneRole = players.get(i).laneRole();
      if (laneRole != null && laneRole == 2 && team.get(i).lane() == LanePosition.MID) {
        mids.add(i);
      }
    }
    if (mids.size() != 1) {
      return score;
    }
    double[][] locked = new double[ROLES][];
    for (int i = 0; i < ROLES; i++) {
      locked[i] = score[i].clone();
    }
    for (int role = 0; role < ROLES; role++) {
      if (role != MID_ROLE - 1) {
        locked[mids.getFirst()][role] = NOT_OPEN;
      }
    }
    return locked;
  }

  private static double knownShare(FunctionProfile profile) {
    List<RoleFunction> scored = RoleFunction.scoredFunctions();
    long known = scored.stream().filter(f -> profile.of(f) != null).count();
    return known / (double) scored.size();
  }
}
