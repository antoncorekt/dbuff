package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildFitWordingTest {
  private static BuildGap noEscape(int deaths) {
    return new BuildGap(RoleFunction.ESCAPE, true, BuildGap.Evidence.DIED_ALONE, deaths);
  }

  @Test
  void aMissingEscapeSaysHowOftenHeDiedAlone() {
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, List.of(noEscape(4))))
        .isEqualTo("Pudge had no escape and died alone 4 times.");
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, List.of(noEscape(2))))
        .contains("died alone twice");
  }

  @Test
  void aWeakEscapeIsNotCalledMissing() {
    BuildGap weak = new BuildGap(RoleFunction.ESCAPE, false, BuildGap.Evidence.DIED_ALONE, 3);

    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 3, List.of(weak)))
        .isEqualTo("Pudge had only a weak escape and died alone 3 times.");
  }

  @Test
  void aMissingFarmSkillSaysTheNetWorthPlace() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 5);

    assertThat(BuildFitWording.verdict("Anti-Mage", BuildFitStage.BOTH, 1, List.of(farm)))
        .isEqualTo("Anti-Mage had no farming skill and finished 5th of 5 in team net worth.");
  }

  @Test
  void aTiedPlaceNamesBothPlaces() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 4.5);

    assertThat(BuildFitWording.verdict("Anti-Mage", BuildFitStage.BOTH, 1, List.of(farm)))
        .isEqualTo(
            "Anti-Mage had no farming skill and finished tied 4th and 5th of 5 in team net worth.");
  }

  @Test
  void severalGapsAreSeveralSentences() {
    BuildGap farm =
        new BuildGap(RoleFunction.FARM_PRIORITY, true, BuildGap.Evidence.LOW_NET_WORTH, 4);

    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.BOTH, 1, List.of(noEscape(3), farm)))
        .isEqualTo(
            "Pudge had no escape and died alone 3 times."
                + " Pudge had no farming skill and finished 4th of 5 in team net worth.");
  }

  @Test
  void noGapSaysTheBuildFit() {
    assertThat(BuildFitWording.verdict("Axe", BuildFitStage.BOTH, 3, List.of()))
        .isEqualTo("Axe's build fit the offlane role.");
  }

  @Test
  void aVerdictOfOneOfTwoRolesNamesIt() {
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.INTENT, 5, List.of(noEscape(3))))
        .startsWith("Drafted as hard support: Pudge had no escape");
    assertThat(BuildFitWording.verdict("Pudge", BuildFitStage.FINAL, 1, List.of()))
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

      assertThat(BuildFitWording.verdict("X", BuildFitStage.BOTH, 1, List.of(gap)))
          .doesNotContain("_");
    }
  }
}
