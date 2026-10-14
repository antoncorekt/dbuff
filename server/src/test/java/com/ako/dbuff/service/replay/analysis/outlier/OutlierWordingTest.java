package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.service.replay.analysis.outlier.OutlierWording.Facts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutlierWordingTest {
  private static final IncomeSource.Main LANE =
      new IncomeSource.Main(IncomeSource.LANE_CREEPS, 0.7);

  @Test
  void saysTheIncomeBreakTheSourceAndTheFights() {
    String text = OutlierWording.sentence(new Facts(false, 12, LANE, 4, 12, 8, 0, null, 0));

    assertThat(text)
        .isEqualTo(
            "his income rose from minute 12; his gold came mostly from lane creeps; he joined 4 of"
                + " 12 fights, fewer than the team median of 8");
  }

  @Test
  void aSmallShareOfTheGainIsMainlyAndAHalfMedianKeepsItsDecimal_andASourceNeedsABreak() {
    var main = new IncomeSource.Main(IncomeSource.BUILDINGS, 0.4);

    assertThat(OutlierWording.sentence(new Facts(false, null, main, 8, 11, 7.5, 0, null, 0)))
        .isEqualTo("he joined 8 of 11 fights, more than the team median of 7.5");
    assertThat(OutlierWording.sentence(new Facts(false, 6, main, 8, 11, 7.5, 0, null, 0)))
        .isEqualTo(
            "his income rose from minute 6; his gold came mainly from buildings; he joined 8 of 11"
                + " fights, more than the team median of 7.5");
  }

  @Test
  void aFeedersBreakAndSourceAreTwoSeparateStatements() {
    assertThat(OutlierWording.sentence(new Facts(true, 8, LANE, 5, 0, 5, 0, null, 0)))
        .isEqualTo("his income fell behind from minute 8; his gold came mostly from lane creeps");
  }

  @Test
  void onlyReasonsWithWordingCanBeNamed() {
    assertThat(OutlierWording.hasDeathWording("CAUGHT_OUT")).isTrue();
    assertThat(OutlierWording.hasDeathWording("OTHER")).isFalse();
    assertThat(OutlierWording.hasDeathWording(null)).isFalse();
  }

  @Test
  void aFeederNamesTheDeathsAndTheirCommonestReason() {
    String text = OutlierWording.sentence(new Facts(true, 8, null, 5, 12, 5, 9, "CAUGHT_OUT", 6));

    assertThat(text)
        .isEqualTo(
            "he died 9 times in that stretch, 6 of them caught out; his income fell behind from"
                + " minute 8; he joined 5 of 12 fights, the same as the team median of 5");
  }

  @Test
  void allDeathsWithOneReasonAndASingleDeath() {
    assertThat(OutlierWording.sentence(new Facts(true, null, null, 1, 0, 1, 5, "TOWER", 5)))
        .isEqualTo("he died 5 times in that stretch, all of them to a tower");
    assertThat(OutlierWording.sentence(new Facts(true, null, null, 1, 0, 1, 1, "BURST", 1)))
        .isEqualTo("he died once in that stretch, burst down");
  }

  @Test
  void anUnknownReasonLeavesOutTheReasonAndNothingKnownGivesAnEmptySentence() {
    assertThat(OutlierWording.sentence(new Facts(true, null, null, 0, 0, 0, 3, "OTHER", 3)))
        .isEqualTo("he died 3 times in that stretch");
    assertThat(OutlierWording.sentence(new Facts(false, null, null, 0, 0, 0, 0, null, 0)))
        .isEmpty();
  }

  @Test
  void neverLongerThanTheColumn() {
    var facts = new Facts(true, 8, LANE, 5, 12, 5, 9, "CAUGHT_OUT", 6);

    assertThat(OutlierWording.sentence(facts)).hasSizeLessThanOrEqualTo(OutlierWording.MAX_LENGTH);
  }
}
