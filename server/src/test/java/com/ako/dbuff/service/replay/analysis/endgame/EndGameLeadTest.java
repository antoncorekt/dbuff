package com.ako.dbuff.service.replay.analysis.endgame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndGameLeadTest {
  private static EndGameLead of(double[] gold) {
    double[] share = new double[gold.length];
    for (int i = 0; i < gold.length; i++) {
      share[i] = gold[i] / 100000.0;
    }
    return EndGameLead.of(gold, share, new EndGameLead.Rules(2000, 0.03, 0.10, 3000, 3));
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
    EndGameLead lead = of(new double[] {0, 0, 0, 0, 12000, 15000, 11000, 2000, 10000, -3000});

    assertThat(lead.heldSeconds()).isEqualTo(180);
  }

  @Test
  void aSingleSampleCountsAsAMinute() {
    assertThat(of(new double[] {0, 0, 0, 20000, 0}).heldSeconds()).isEqualTo(60);
  }

  @Test
  void aLeadBelowTheMinimumGoldOrShareIsNotALead() {
    assertThat(of(new double[] {0, 656, 1500, -1000}).everLed()).isFalse();
    EndGameLead lowShare =
        EndGameLead.of(
            new double[] {0, 5000},
            new double[] {0, 0.02},
            new EndGameLead.Rules(2000, 0.03, 0.1, 3000, 0));
    assertThat(lowShare.everLed()).isFalse();
  }

  @Test
  void samplesBeforeTheThrowMinuteOrBelowTheThrowGoldDoNotHold() {
    assertThat(of(new double[] {20000, 20000, 20000, 0, 0}).heldSeconds()).isZero();
    EndGameLead small =
        EndGameLead.of(
            new double[] {0, 0, 0, 0, 2500, 2500},
            new double[] {0, 0, 0, 0, 0.2, 0.2},
            new EndGameLead.Rules(2000, 0.03, 0.1, 3000, 3));
    assertThat(small.heldSeconds()).isZero();
  }

  @Test
  void theLastSampleIsKept() {
    assertThat(of(new double[] {0, 4000, 6000}).lastGold()).isEqualTo(6000);
  }
}
