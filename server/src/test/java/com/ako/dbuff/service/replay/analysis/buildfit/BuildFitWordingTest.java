package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildFitWordingTest {
  private static BuildGap noEscape(int deaths) {
    return new BuildGap(RoleFunction.ESCAPE, true, BuildGap.Evidence.DIED_ALONE, deaths);
  }

  private static BuildFitJudge.Verdict gaps(BuildGap... gaps) {
    return new BuildFitJudge.Verdict(
        java.util.Arrays.stream(gaps).map(BuildGap::function).toList(), List.of(gaps), List.of());
  }

  @Test
  void lacksTheMatchDidNotShowAreSaidSo() {
    BuildFitJudge.Verdict verdict =
        new BuildFitJudge.Verdict(
            List.of(RoleFunction.ESCAPE, RoleFunction.DISABLE), List.of(), List.of());

    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 1, verdict))
        .isEqualTo("Pudge's build lacks escape and disable, but the match did not show it.");
  }

  @Test
  void anUnknownFunctionIsNamedAndNeverCalledFitting() {
    BuildFitJudge.Verdict none =
        new BuildFitJudge.Verdict(List.of(), List.of(), List.of(RoleFunction.DAMAGE));
    BuildFitJudge.Verdict lacking =
        new BuildFitJudge.Verdict(
            List.of(RoleFunction.ESCAPE),
            List.of(),
            List.of(RoleFunction.DAMAGE, RoleFunction.DISABLE));

    assertThat(BuildFitWording.verdict("Axe", BuildFitStage.BOTH, 3, none))
        .isEqualTo("Axe's build showed no gap in the offlane role. Damage could not be measured.")
        .doesNotContain("fit the");
    assertThat(BuildFitWording.verdict("Axe", BuildFitStage.BOTH, 3, lacking))
        .isEqualTo(
            "Axe's build lacks escape, but the match did not show it."
                + " Damage and disable could not be measured.");
  }

  @Test
  void aMissingEscapeSaysHowOftenHeDiedAlone() {
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, gaps(noEscape(4))))
        .isEqualTo("Pudge had no escape and died alone 4 times.");
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, gaps(noEscape(2))))
        .contains("died alone twice");
  }

  @Test
  void aWeakEscapeIsNotCalledMissing() {
    BuildGap weak = new BuildGap(RoleFunction.ESCAPE, false, BuildGap.Evidence.DIED_ALONE, 3);

    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, gaps(weak)))
        .isEqualTo("Pudge had only a weak escape and died alone 3 times.");
  }

  @Test
  void aMissingFarmSkillSaysTheNetWorthPlace() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 5);

    assertThat(BuildFitWording.verdict("Anti-Mage", BuildFitStage.BOTH, 1, gaps(farm)))
        .isEqualTo("Anti-Mage had no farming skill and finished 5th of 5 in team net worth.");
  }

  @Test
  void aTiedPlaceNamesBothPlaces() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 4.5);

    assertThat(BuildFitWording.verdict("Anti-Mage", BuildFitStage.BOTH, 1, gaps(farm)))
        .isEqualTo(
            "Anti-Mage had no farming skill and finished tied 4th and 5th of 5 in team net worth.");
  }

  @Test
  void severalGapsAreSeveralSentences() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 4);

    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 1, gaps(noEscape(3), farm)))
        .isEqualTo(
            "Pudge had no escape and died alone 3 times."
                + " Pudge had no farming skill and finished 4th of 5 in team net worth.");
  }

  @Test
  void noGapSaysTheBuildFit() {
    assertThat(BuildFitWording.verdict("Axe", BuildFitStage.BOTH, 3, gaps()))
        .isEqualTo("Axe's build fit the offlane role.");
  }

  @Test
  void aVerdictOfOneOfTwoRolesNamesIt() {
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.INTENT, 5, gaps(noEscape(3))))
        .startsWith("Drafted as hard support: Pudge had no escape");
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.FINAL, 1, gaps()))
        .isEqualTo("Played carry: Pudge's build fit the carry role.");
  }

  @Test
  void aChangeOfRoleIsSaidInPlainWords() {
    assertThat(BuildFitWording.roleChange("Pudge", 5, 1))
        .isEqualTo("Pudge was drafted as hard support and played carry.");
  }

  @Test
  void everyFunctionAMissingGapCanNameHasWords() {
    for (RoleFunction function : RoleFunction.values()) {
      BuildGap gap = new BuildGap(function, true, BuildGap.Evidence.DIED_ALONE, 2);

      assertThat(BuildFitWording.verdict("X", BuildFitStage.BOTH, 1, gaps(gap)))
          .doesNotContain("_");
    }
  }
}
