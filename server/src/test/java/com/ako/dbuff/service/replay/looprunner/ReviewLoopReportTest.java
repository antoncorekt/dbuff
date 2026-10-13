package com.ako.dbuff.service.replay.looprunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewLoopReportTest {
  @Test
  void anUnavailableMatchIsReportedWithItsReason() {
    assertThat(ReviewLoopReport.unavailable(5L, "no fixture"))
        .isEqualTo("# Match 5\n\nNot reported: no fixture\n");
  }

  @Test
  void anUnavailableReportIsRecognisedAndItsReasonRead() {
    String report = ReviewLoopReport.unavailable(5L, "skipped: a\nb");

    assertThat(ReviewLoopReport.isUnavailable(report)).isTrue();
    assertThat(ReviewLoopReport.reasonOf(report)).isEqualTo("skipped: a b");
    assertThat(ReviewLoopReport.isUnavailable("# Match 5\n\n## Roles\n")).isFalse();
  }

  @Test
  void theIntroductionIsTheHowToReadLineThenTheSummaryLine() {
    assertThat(ReviewLoopReport.introduction("read it so", "3 things"))
        .containsExactly("**How to read this:** read it so", "", "**Summary:** 3 things");
  }
}
