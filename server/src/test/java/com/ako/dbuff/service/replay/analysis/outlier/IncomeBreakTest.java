package com.ako.dbuff.service.replay.analysis.outlier;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBreakTest {
  private static double[] series(double start, double... gains) {
    double[] worth = new double[gains.length + 1];
    worth[0] = start;
    for (int i = 0; i < gains.length; i++) {
      worth[i + 1] = worth[i] + gains[i];
    }
    return worth;
  }

  private static final double[] STEADY = series(0, 100, 100, 100, 100, 100, 100, 100);

  @Test
  void findsTheFirstMinuteOfASustainedGap() {
    double[] own = series(0, 100, 100, 300, 300, 300, 300, 300);

    OptionalInt found = IncomeBreak.find(own, List.of(STEADY, STEADY), true, 3, 0.25, 4, 6);

    assertThat(found).hasValue(2);
  }

  @Test
  void aShortGapIsNoBreak() {
    double[] own = series(0, 100, 100, 300, 300, 100, 100, 100);

    assertThat(IncomeBreak.find(own, List.of(STEADY), true, 3, 0.25, 0, 7)).isEmpty();
  }

  @Test
  void aBreakThatEndedBeforeTheWindowDoesNotCount() {
    double[] own = series(0, 300, 300, 300, 100, 100, 100, 100);

    assertThat(IncomeBreak.find(own, List.of(STEADY), true, 3, 0.25, 5, 7)).isEmpty();
    assertThat(IncomeBreak.find(own, List.of(STEADY), true, 3, 0.25, 2, 7)).hasValue(0);
  }

  @Test
  void aFallingIncomeIsABreakOnlyDownwards() {
    double[] own = series(0, 100, 100, 20, 20, 20, 20, 20);

    assertThat(IncomeBreak.find(own, List.of(STEADY), false, 3, 0.25, 0, 7)).hasValue(2);
    assertThat(IncomeBreak.find(own, List.of(STEADY), true, 3, 0.25, 0, 7)).isEmpty();
  }

  @Test
  void aGapExactlyAtTheShareCounts() {
    double[] own = series(0, 125, 125, 125, 100, 100, 100, 100);

    assertThat(IncomeBreak.find(own, List.of(STEADY), true, 3, 0.25, 0, 7)).hasValue(0);
  }

  @Test
  void aTeamOfOneOrTooShortASeriesHasNothingToCompareWith() {
    assertThat(IncomeBreak.find(STEADY, List.of(), true, 3, 0.25, 0, 7)).isEmpty();
    assertThat(IncomeBreak.find(new double[] {1}, List.of(new double[] {1}), true, 1, 0.25, 0, 0))
        .isEmpty();
  }

  @Test
  void aMateWithAShorterSeriesLimitsTheComparison() {
    double[] own = series(0, 300, 300, 300, 300, 300, 300, 300);

    assertThat(IncomeBreak.find(own, List.of(series(0, 100, 100, 100)), true, 3, 0.25, 0, 2))
        .hasValue(0);
  }

  @Test
  void ofSeveralStretchesTouchingTheWindowTheLatestStartingWins() {
    double[] own = series(0, 300, 300, 300, 100, 100, 300, 300, 300);
    double[] steady = series(0, 100, 100, 100, 100, 100, 100, 100, 100);

    assertThat(IncomeBreak.find(own, List.of(steady), true, 3, 0.25, 0, 8)).hasValue(5);
    assertThat(IncomeBreak.find(own, List.of(steady), true, 3, 0.25, 0, 4)).hasValue(0);
  }

  @Test
  void theWindowBoundsAreInclusiveMinuteIndexes() {
    double[] early = series(0, 300, 300, 300, 100, 100, 100, 100);
    double[] late = series(0, 100, 100, 100, 100, 300, 300, 300);

    assertThat(IncomeBreak.find(early, List.of(STEADY), true, 3, 0.25, 2, 7)).hasValue(0);
    assertThat(IncomeBreak.find(early, List.of(STEADY), true, 3, 0.25, 3, 7)).isEmpty();
    assertThat(IncomeBreak.find(late, List.of(STEADY), true, 3, 0.25, 0, 4)).hasValue(4);
    assertThat(IncomeBreak.find(late, List.of(STEADY), true, 3, 0.25, 0, 3)).isEmpty();
  }
}
