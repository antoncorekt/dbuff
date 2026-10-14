package com.ako.dbuff.service.replay.analysis.endgame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndGameLeadTest {
  private static EndGameLead of(double[] gold) {
    double[] share = new double[gold.length];
    for (int i = 0; i < gold.length; i++) {
      share[i] = gold[i] / 100000.0;
    }
    return EndGameLead.of(gold, share, 0.10);
  }

  @Test
  void aLoserWhoNeverLedHasNoPeak() {
    EndGameLead lead = of(new double[] {0, -1000, -5000, -9000});

    assertThat(lead.everLed()).isFalse();
    assertThat(lead.heldSeconds()).isZero();
    assertThat(lead.lost()).isTrue();
  }

  @Test
  void thePeakIsTheLargestLeadInGoldAndItsMinute() {
    EndGameLead lead = of(new double[] {0, 3000, 9000, 6000, -2000});

    assertThat(lead.peakMinute()).isEqualTo(2);
    assertThat(lead.peakGold()).isEqualTo(9000);
    assertThat(lead.peakShare()).isEqualTo(0.09);
    assertThat(lead.lost()).isTrue();
  }

  @Test
  void aLeadStillHeldAtTheEndIsNotLost() {
    assertThat(of(new double[] {0, 4000, 8000, 3000}).lost()).isFalse();
  }

  @Test
  void heldSecondsCountsTheLongestRunAtOrAboveTheShare() {
    EndGameLead lead = of(new double[] {0, 12000, 15000, 11000, 2000, 10000, -3000});

    assertThat(lead.heldSeconds()).isEqualTo(180);
  }

  @Test
  void aSingleSampleCountsAsAMinute() {
    assertThat(of(new double[] {0, 20000, 0}).heldSeconds()).isEqualTo(60);
  }
}
