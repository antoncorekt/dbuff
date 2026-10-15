package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitJudge.Evidence;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitJudge.Verdict;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildFitJudgeTest {
  private final BuildFitJudge judge =
      new BuildFitJudge(new RoleNeedsFile("replay/role-needs.json"), new BuildFitProperties());

  private static FunctionProfile build(Double farm, Double damage, Double escape) {
    return new FunctionProfile(null, farm, 0.0, 0.0, null, 0.5, 0.5, 0.5, damage, 0.5, escape);
  }

  private static Evidence evidence(int lonelyDeaths, double place) {
    return new Evidence(lonelyDeaths, place);
  }

  @Test
  void aMissingEscapeIsStatedOnlyWhenThePlayerDiedAloneEnough() {
    Verdict alone = judge.judge(build(0.8, 0.8, 0.0), 1, evidence(4, 1));
    Verdict rarely = judge.judge(build(0.8, 0.8, 0.0), 1, evidence(1, 1));

    assertThat(alone.missing()).containsExactly(RoleFunction.ESCAPE);
    assertThat(alone.gaps())
        .containsExactly(new BuildGap(RoleFunction.ESCAPE, true, BuildGap.Evidence.DIED_ALONE, 4));
    assertThat(rarely.missing()).containsExactly(RoleFunction.ESCAPE);
    assertThat(rarely.gaps()).isEmpty();
  }

  @Test
  void aMissingFarmSkillIsStatedOnlyWithALowNetWorthPlace() {
    Verdict poor = judge.judge(build(0.2, 0.8, 0.9), 1, evidence(0, 5));
    Verdict rich = judge.judge(build(0.2, 0.8, 0.9), 1, evidence(0, 1));

    assertThat(poor.missing()).containsExactly(RoleFunction.FARM_PRIORITY);
    assertThat(poor.gaps())
        .containsExactly(
            new BuildGap(RoleFunction.FARM_PRIORITY, false, BuildGap.Evidence.LOW_NET_WORTH, 5));
    assertThat(rich.missing()).containsExactly(RoleFunction.FARM_PRIORITY);
    assertThat(rich.gaps()).isEmpty();
  }

  @Test
  void aMissingFunctionWithNoEvidenceRuleIsMissingButNeverStated() {
    Verdict verdict = judge.judge(build(0.8, 0.1, 0.9), 1, evidence(9, 5));

    assertThat(verdict.missing()).containsExactly(RoleFunction.DAMAGE);
    assertThat(verdict.gaps()).isEmpty();
  }

  @Test
  void anUnknownFunctionIsNeverMissing() {
    Verdict verdict = judge.judge(build(null, null, null), 1, evidence(9, 5));

    assertThat(verdict.missing()).isEmpty();
    assertThat(verdict.gaps()).isEmpty();
  }

  @Test
  void anUnknownNetWorthPlaceBacksNothing() {
    Verdict verdict = judge.judge(build(0.1, 0.8, 0.9), 1, evidence(0, Double.NaN));

    assertThat(verdict.gaps()).isEmpty();
  }

  @Test
  void aFullBuildHasNothingMissing() {
    Verdict verdict = judge.judge(build(0.9, 0.9, 0.9), 1, evidence(5, 5));

    assertThat(verdict.missing()).isEmpty();
    assertThat(verdict.gaps()).isEmpty();
  }

  @Test
  void everyRoleWantsAnEscapeEvenWhenItsNeedsDoNotListOne() {
    for (int role = 1; role <= 5; role++) {
      assertThat(judge.judge(build(0.9, 0.9, 0.0), role, evidence(3, 1)).gaps())
          .as("role " + role)
          .extracting(BuildGap::function)
          .contains(RoleFunction.ESCAPE);
    }
  }
}
