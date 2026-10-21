package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitJudge.Evidence;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitJudge.Verdict;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void aRoleNeedNamingEscapeStopsStartup() {
    RoleNeeds conflicting = role -> role == 4 ? Map.of(RoleFunction.ESCAPE, 0.3) : Map.of();

    assertThatThrownBy(new BuildFitJudge(conflicting, new BuildFitProperties())::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("role 4")
        .hasMessageContaining("escape-bonus-minimum");
    assertThatCode(judge::validate).doesNotThrowAnyException();
  }

  @Test
  void anUnknownEscapeLeavesTheOtherFunctionsJudged() {
    Verdict verdict = judge.judge(build(0.2, 0.8, null), 1, evidence(9, 5));

    assertThat(verdict.missing()).containsExactly(RoleFunction.FARM_PRIORITY);
    assertThat(verdict.gaps()).hasSize(1);
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
  void anUnknownFunctionIsNeverMissingButIsNamed() {
    Verdict verdict = judge.judge(build(null, null, null), 1, evidence(9, 5));

    assertThat(verdict.missing()).isEmpty();
    assertThat(verdict.gaps()).isEmpty();
    assertThat(verdict.unknown())
        .containsExactlyInAnyOrder(
            RoleFunction.FARM_PRIORITY, RoleFunction.DAMAGE, RoleFunction.ESCAPE);
  }

  @Test
  void nothingMissingIsFit() {
    assertThat(judge.judge(build(0.9, 0.9, 0.9), 1, evidence(5, 5)).level())
        .isEqualTo(BuildFitVerdict.FIT);
  }

  @Test
  void aLackTheMatchDoesNotBackIsGapsNotShown() {
    assertThat(judge.judge(build(0.8, 0.8, 0.0), 1, evidence(1, 1)).level())
        .isEqualTo(BuildFitVerdict.GAPS_NOT_SHOWN);
  }

  @Test
  void aBackedLackIsAGapEvenBesideAnUnbackedOne() {
    assertThat(judge.judge(build(0.8, 0.1, 0.0), 1, evidence(4, 1)).level())
        .isEqualTo(BuildFitVerdict.GAP);
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
