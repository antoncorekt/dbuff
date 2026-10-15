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
    assertThat(dip.before()).isEqualTo(820);
    assertThat(dip.lowest()).isEqualTo(300);
    assertThat(dip.drop()).isEqualTo(520);
    assertThat(dip.causeFromSeconds()).isEqualTo(180);
    assertThat(dip.toSeconds()).isEqualTo(480);
  }

  @Test
  void aSmallWobbleIsNotADip() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 500, 480, 430, 450, 500};

    assertThat(IncomeDips.find(gpm)).isEmpty();
  }

  @Test
  void aGapSplitsTwoStretchesWithoutThrowing() {
    double[] gpm = {Double.NaN, Double.NaN, Double.NaN, 900, 900, 300, Double.NaN, 300};

    assertThat(IncomeDips.find(gpm)).hasSize(2);
  }
}
