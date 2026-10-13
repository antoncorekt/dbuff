package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.service.replay.analysis.importance.ImportantFightRule.Measurement;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportantFightRuleTest {
  private final ImportantFightRule rule = new ImportantFightRule(new ImportantFightProperties());

  private static AdvantageSeries series(double[] gold, double[] xp, double netWorth) {
    double[] nw = new double[gold.length];
    Arrays.fill(nw, netWorth);
    return new AdvantageSeries(gold, xp, nw, nw.clone());
  }

  private static double[] flat(int minutes) {
    return new double[minutes];
  }

  @Test
  void aGoldGainOfTheMinimumGoldIsImportant_evenOfALargeNetWorth() {
    double[] gold = {0, 0, 0, 1000, 3000, 3000, 3000};
    AdvantageSeries s = series(gold, flat(7), 100_000);

    Measurement m = rule.measure(s, TeamSide.RADIANT, 120, 140);

    assertThat(m.goldChange()).isEqualTo(3000);
    assertThat(rule.isImportant(m)).isTrue();
  }

  @Test
  void aGainOfThreePercentOfTheNetWorthIsImportantAndTwoAndAHalfIsNot() {
    double[] big = {0, 0, 0, 700, 700, 700};
    double[] small = {0, 0, 0, 500, 500, 500};

    Measurement three = rule.measure(series(big, flat(6), 20_000), TeamSide.RADIANT, 120, 130);
    Measurement two = rule.measure(series(small, flat(6), 20_000), TeamSide.RADIANT, 120, 130);

    assertThat(three.sharePercent()).isEqualTo(3.5);
    assertThat(rule.isImportant(three)).isTrue();
    assertThat(rule.isImportant(two)).isFalse();
  }

  @Test
  void experienceAloneNeverMakesAFightImportant() {
    double[] xp = {0, 0, 0, 5000, 9000, 9000};

    Measurement m = rule.measure(series(flat(6), xp, 20_000), TeamSide.RADIANT, 120, 130);

    assertThat(m.xpChange()).isEqualTo(9000);
    assertThat(rule.isImportant(m)).isFalse();
  }

  @Test
  void theChangeIsSignedForTheWinner_aWinnerWhoLostGoldIsNotImportant() {
    double[] gold = {0, 0, 0, 2000, 4000, 4000};

    Measurement dire = rule.measure(series(gold, flat(6), 20_000), TeamSide.DIRE, 120, 130);
    Measurement radiant = rule.measure(series(gold, flat(6), 20_000), TeamSide.RADIANT, 120, 130);

    assertThat(dire.goldChange()).isEqualTo(-4000);
    assertThat(rule.isImportant(dire)).isFalse();
    assertThat(rule.isImportant(radiant)).isTrue();
  }

  @Test
  void aLeadFlipToTheWinnerMakesASmallFightImportant_aFlipToTheLoserDoesNot() {
    double[] gold = {-300, -300, -300, -100, 100, 100};
    AdvantageSeries s = series(gold, flat(6), 100_000);

    Measurement toWinner = rule.measure(s, TeamSide.RADIANT, 150, 160);
    Measurement toLoser = rule.measure(s, TeamSide.DIRE, 150, 160);

    assertThat(toWinner.leadFlip()).isTrue();
    assertThat(toWinner.flipToWinner()).isTrue();
    assertThat(toWinner.goldChange()).isLessThan(1500);
    assertThat(rule.isImportant(toWinner)).isTrue();
    assertThat(toLoser.leadFlip()).isTrue();
    assertThat(toLoser.flipToWinner()).isFalse();
    assertThat(rule.isImportant(toLoser)).isFalse();
  }

  @Test
  void aFightShorterThanAMinuteIsMeasuredOverAMinutePlusTheLag_withLinearInterpolation() {
    double[] gold = {0, 0, 1000, 2000, 2000, 2000};
    AdvantageSeries s = series(gold, flat(6), 100_000);

    Measurement m = rule.measure(s, TeamSide.RADIANT, 90, 95);

    assertThat(m.goldChange()).isEqualTo(1500);
  }

  @Test
  void aFightAtTheEndOfTheSeriesIsMeasuredUpToTheLastMinute() {
    double[] gold = {0, 0, 0, 2000};

    Measurement m = rule.measure(series(gold, flat(4), 100_000), TeamSide.RADIANT, 120, 130);

    assertThat(m.goldChange()).isEqualTo(2000);
  }

  @Test
  void aSlopeBreakIsAGraphThatTurnsAroundTheFight() {
    double[] gold = {0, 1000, 2000, 3000, 3200, 2400, 1600, 800};
    double[] steady = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000};

    assertThat(
            rule.measure(series(gold, flat(8), 100_000), TeamSide.RADIANT, 180, 190).slopeBreak())
        .isTrue();
    assertThat(
            rule.measure(series(steady, flat(8), 100_000), TeamSide.RADIANT, 180, 190).slopeBreak())
        .isFalse();
  }
}
