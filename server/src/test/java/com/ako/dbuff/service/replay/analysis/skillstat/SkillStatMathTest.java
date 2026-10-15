package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SkillStatMathTest {

  private static SkillPickStatDomain pair(long games, long wins, long[] without) {
    return SkillPickStatDomain.builder()
        .kind(SkillStatKind.SKILL_PAIR)
        .games(games)
        .wins(wins)
        .firstGamesWithout(without[0])
        .firstWinsWithout(without[1])
        .secondGamesWithout(without[2])
        .secondWinsWithout(without[3])
        .build();
  }

  @Test
  void aSmallSampleNeverRanksAboveASolidOneWithTheSameRate() {
    assertThat(SkillStatMath.wilsonLowerBound(3, 3))
        .isLessThan(SkillStatMath.wilsonLowerBound(60, 100));
    assertThat(SkillStatMath.wilsonLowerBound(50, 100)).isCloseTo(0.4038, within(0.001));
  }

  @Test
  void noGamesRankZero() {
    assertThat(SkillStatMath.wilsonLowerBound(0, 0)).isZero();
  }

  @Test
  void liftIsThePairRateMinusTheAverageOfTheMembersRatesWithoutThePair() {
    SkillPickStatDomain row = pair(14, 8, new long[] {20, 8, 10, 6});

    assertThat(SkillStatMath.liftPoints(row).getAsDouble())
        .isCloseTo(100 * (8 / 14.0 - (0.4 + 0.6) / 2), within(1e-9));
  }

  @Test
  void aMemberWithNoGamesWithoutThePairLeavesTheOtherAsTheBaseline() {
    SkillPickStatDomain row = pair(10, 6, new long[] {0, 0, 10, 5});

    assertThat(SkillStatMath.liftPoints(row).getAsDouble()).isCloseTo(10.0, within(1e-9));
  }

  @Test
  void noBaselineMeansNoLift() {
    assertThat(SkillStatMath.liftPoints(pair(10, 6, new long[] {0, 0, 0, 0}))).isEmpty();
  }

  @Test
  void aPairNeverSeenOrAnotherKindHasNoLift() {
    assertThat(SkillStatMath.liftPoints(pair(0, 0, new long[] {5, 2, 5, 2}))).isEmpty();
    assertThat(
            SkillStatMath.liftPoints(
                SkillPickStatDomain.builder().kind(SkillStatKind.SKILL).games(5L).wins(2L).build()))
        .isEmpty();
  }
}
