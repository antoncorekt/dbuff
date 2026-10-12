package com.ako.dbuff.service.replay.looprunner;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewLoopFilesTest {
  @TempDir Path base;

  @Test
  void theMatchListIgnoresCommentsBlanksAndRepeats() {
    assertThat(
            ReviewLoopMatchList.parse(
                List.of("# Ability Draft", "", " 11 ", "22 # a note", "11", "  # x"), "list"))
        .containsExactly(11L, 22L);
  }

  @Test
  void theFirstRoundIsOneAndTheNextFollowsTheLastExisting() {
    assertThat(ReviewLoopRound.open(base, null, false).number()).isEqualTo(1);
    new ReviewLoopRound(base, 3).write("a.md", "x");

    ReviewLoopRound next = ReviewLoopRound.open(base, null, false);

    assertThat(next.number()).isEqualTo(4);
    assertThat(next.dir().getFileName().toString()).isEqualTo("round-04");
    assertThat(ReviewLoopRound.open(base, "2", false).number()).isEqualTo(2);
  }

  @Test
  void thePreviousReportIsFromTheLatestEarlierRoundThatHasIt() {
    new ReviewLoopRound(base, 1).write("a.md", "one");
    new ReviewLoopRound(base, 2).write("b.md", "other");

    ReviewLoopRound third = new ReviewLoopRound(base, 3);

    assertThat(third.previous("a.md", r -> true)).contains("one");
    assertThat(new ReviewLoopRound(base, 1).previous("a.md", r -> true)).isEmpty();
  }

  @Test
  void aMalformedLineIsNamedWithItsFileAndNumber() {
    assertThatThrownBy(
            () -> ReviewLoopMatchList.parse(List.of("11", "# ok", "12ab"), "matches.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matches.txt line 3: not a match id: '12ab'");
  }

  @Test
  void aBadRoundNumberIsExplained() {
    assertThatThrownBy(() -> ReviewLoopRound.open(base, "two", false))
        .hasMessageContaining("ROUND must be a positive whole number, got 'two'");
    assertThatThrownBy(() -> ReviewLoopRound.open(base, "0", false))
        .hasMessageContaining("positive whole number");
  }

  @Test
  void anExplicitRoundWithReportsIsNotOverwrittenUnlessAsked() {
    new ReviewLoopRound(base, 2).write("a.md", "x");

    assertThatThrownBy(() -> ReviewLoopRound.open(base, "2", false))
        .hasMessageContaining("ROUND_OVERWRITE=1");
    assertThat(ReviewLoopRound.open(base, "2", true).number()).isEqualTo(2);
    assertThat(ReviewLoopRound.open(base, "3", false).number()).isEqualTo(3);
  }

  @Test
  void aSkippedReportIsNotTheBaseline() {
    new ReviewLoopRound(base, 1).write("a.md", "# Match 1\n\n## Roles\n");
    new ReviewLoopRound(base, 2).write("a.md", ReviewLoopReport.unavailable(1, "no fixture"));

    assertThat(
            new ReviewLoopRound(base, 3).previous("a.md", r -> !ReviewLoopReport.isUnavailable(r)))
        .contains("# Match 1\n\n## Roles\n");
  }
}
