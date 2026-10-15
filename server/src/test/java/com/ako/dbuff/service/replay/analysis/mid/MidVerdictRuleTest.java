package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.service.replay.analysis.mid.MidVerdictRule.Facts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MidVerdictRuleTest {
  private final MidVerdictRule rule = new MidVerdictRule(new MidActivityProperties());

  @Test
  void rotatingAndKillingIsActive() {
    assertThat(rule.verdict(new Facts(MidNext.ROTATED, 1, 0, 0, null, null)))
        .isEqualTo(MidVerdict.ACTIVE);
  }

  @Test
  void rotatingWithoutKillsIsNotEnough() {
    assertThat(rule.verdict(new Facts(MidNext.ROTATED, 0, 0, 3, 500.0, 600.0)))
        .isEqualTo(MidVerdict.PASSIVE);
  }

  @Test
  void killsAfterAFightOrJungleTripDoNotMakeARotation() {
    assertThat(rule.verdict(new Facts(MidNext.FIGHT, 3, 0, 2, 500.0, 600.0)))
        .isEqualTo(MidVerdict.PASSIVE);
  }

  @Test
  void joiningTheMedianOfFightsAndLeadingInExperienceIsActive() {
    assertThat(rule.verdict(new Facts(MidNext.STAYED_MID, null, 4, 3.5, 700.0, 650.0)))
        .isEqualTo(MidVerdict.ACTIVE);
  }

  @Test
  void fewerFightsThanTheMedianIsPassive() {
    assertThat(rule.verdict(new Facts(MidNext.STAYED_MID, null, 2, 3, 700.0, 650.0)))
        .isEqualTo(MidVerdict.PASSIVE);
  }

  @Test
  void leadingInExperienceWithoutAnyFightIsPassive() {
    assertThat(rule.verdict(new Facts(MidNext.STAYED_MID, null, 0, 0, 700.0, 650.0)))
        .isEqualTo(MidVerdict.PASSIVE);
  }

  @Test
  void anUnknownExperienceComparisonIsNotALeadAndIsNotEnoughData() {
    assertThat(rule.verdict(new Facts(MidNext.STAYED_MID, null, 5, 1, 700.0, null)))
        .isEqualTo(MidVerdict.UNKNOWN);
    assertThat(rule.verdict(new Facts(MidNext.STAYED_MID, null, 5, 1, null, 600.0)))
        .isEqualTo(MidVerdict.UNKNOWN);
  }

  @Test
  void rotatingAndKillingIsActiveEvenWithoutAnEnemyMid() {
    assertThat(rule.verdict(new Facts(MidNext.ROTATED, 2, 0, 0, null, null)))
        .isEqualTo(MidVerdict.ACTIVE);
  }
}
