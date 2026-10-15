package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.IncomeDips.Dip;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeDipsTest {
  private static Map<Integer, Long> gold(long... perMinuteGain) {
    Map<Integer, Long> gold = new TreeMap<>();
    long total = 0;
    gold.put(0, 0L);
    for (int m = 0; m < perMinuteGain.length; m++) {
      total += perMinuteGain[m];
      gold.put(m + 1, total);
    }
    return gold;
  }

  @Test
  void rollingIncomeIsTheGoldOfTheLastThreeMinutesPerMinute() {
    double[] gpm = IncomeDips.rolling(gold(600, 600, 600, 300, 300, 300));

    assertThat(gpm[2]).isNaN();
    assertThat(gpm[3]).isEqualTo(600);
    assertThat(gpm[6]).isEqualTo(300);
  }

  @Test
  void aMissingMinuteLeavesAGapInsteadOfAWrongNumber() {
    Map<Integer, Long> gold = gold(600, 600, 600, 600, 600);
    gold.remove(2);

    double[] gpm = IncomeDips.rolling(gold);

    assertThat(gpm[5]).isNaN();
    assertThat(gpm[4]).isEqualTo(600);
  }

  @Test
  void emptyAndShortSeriesGiveNothing() {
    assertThat(IncomeDips.rolling(Map.of())).isEmpty();
    assertThat(IncomeDips.find(IncomeDips.rolling(gold(500, 500)))).isEmpty();
    assertThat(IncomeDips.find(new double[0])).isEmpty();
  }

  @Test
  void aLongFallFromTheRecentPeakIsOneDipWithItsSize() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 800, 820, 800, 400, 300, 350, 700, 800};

    List<Dip> dips = IncomeDips.find(gpm);

    assertThat(dips).hasSize(1);
    Dip dip = dips.getFirst();
    assertThat(dip.fromMinute()).isEqualTo(6);
    assertThat(dip.toMinute()).isEqualTo(8);
    assertThat(dip.before()).isEqualTo(800);
    assertThat(dip.lowest()).isEqualTo(300);
    assertThat(dip.drop()).isEqualTo(500);
    assertThat(dip.causeFromSeconds()).isEqualTo(180);
    assertThat(dip.toSeconds()).isEqualTo(480);
  }

  @Test
  void aSmallWobbleIsNotADip() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 500, 480, 430, 450, 500};

    assertThat(IncomeDips.find(gpm)).isEmpty();
  }

  @Test
  void aGapInTheSeriesDoesNotBreakTheSearch() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 900, 900, 300, Double.NaN, 300};

    assertThat(IncomeDips.find(gpm)).extracting(Dip::fromMinute).containsExactly(7);
  }

  @Test
  void aDipThenARecoveryThenAnotherDipAreTwoDipsEachAgainstItsOwnPeak() {
    double[] gpm = {
      Double.NaN,
      Double.NaN,
      Double.NaN,
      900,
      900,
      900,
      400,
      900,
      900,
      900,
      900,
      1000,
      300,
      300,
      1000
    };

    List<Dip> dips = IncomeDips.find(gpm);

    assertThat(dips).extracting(Dip::fromMinute).containsExactly(6, 12);
    assertThat(dips.get(0).before()).isEqualTo(900);
    assertThat(dips.get(1).before()).isEqualTo(900);
    assertThat(dips.get(1).toMinute()).isEqualTo(13);
  }

  @Test
  void aPeakFromBeforeARecoveredDipIsNotReusedRightAfterIt() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 1000, 1000, 1000, 300, 900, 800, 600, 580};

    List<Dip> dips = IncomeDips.find(gpm);

    assertThat(dips).hasSize(1);
    assertThat(dips.getFirst().toMinute()).isEqualTo(6);
  }

  @Test
  void consecutiveLowMinutesMergeIntoOneDip() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 900, 900, 900, 500, 350, 600, 650, 900};

    List<Dip> dips = IncomeDips.find(gpm);

    assertThat(dips).hasSize(1);
    assertThat(dips.getFirst().fromMinute()).isEqualTo(6);
    assertThat(dips.getFirst().toMinute()).isEqualTo(9);
    assertThat(dips.getFirst().lowest()).isEqualTo(350);
  }

  @Test
  void spendingGoldOnAnItemIsNotADipBecauseStoredGoldIsCumulativeEarnings() {
    Map<Integer, Long> earned = gold(600, 600, 600, 600, 600, 600, 600, 600);
    double[] steady = IncomeDips.rolling(earned);
    assertThat(IncomeDips.find(steady)).isEmpty();

    Map<Integer, Long> spentFrom = new TreeMap<>(earned);
    spentFrom.put(6, earned.get(6) - 2200);
    double[] notCumulative = IncomeDips.rolling(spentFrom);

    assertThat(notCumulative[6]).isNaN();
    assertThat(IncomeDips.find(notCumulative)).isEmpty();
  }
}
