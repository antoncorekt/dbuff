package com.ako.dbuff.service.replay.analysis.role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignmentSolverTest {

  @Test
  void picksTheBestTotalEvenWhenAPlayerWouldWantAnotherPlayersRole() {
    double[][] score = {
      {5, 4, 0},
      {5, 0, 0},
      {0, 0, 1},
    };

    var solution = RoleAssignmentSolver.solve(score);

    assertThat(solution.roleOf()).containsExactly(1, 0, 2);
  }

  @Test
  void aTieGivesTheFirstAssignmentInRoleOrderAndZeroMargin() {
    var solution = RoleAssignmentSolver.solve(new double[3][3]);

    assertThat(solution.roleOf()).containsExactly(0, 1, 2);
    assertThat(solution.margin()).containsOnly(0.0);
  }

  @Test
  void marginIsWhatTheTotalLosesWhenThePlayerTakesAnyOtherRole() {
    double[][] score = {
      {10, 0},
      {0, 1},
    };

    var solution = RoleAssignmentSolver.solve(score);

    assertThat(solution.roleOf()).containsExactly(0, 1);
    assertThat(solution.margin()).containsExactly(11.0, 11.0);
  }
}
