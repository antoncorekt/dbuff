package com.ako.dbuff.service.replay.roleloop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleLoopFilesTest {
  @TempDir Path base;

  @Test
  void theMatchListIgnoresCommentsBlanksAndRepeats() {
    assertThat(
            RoleLoopMatchList.parse(
                List.of("# Ability Draft", "", " 11 ", "22 # a note", "11", "  # x"), "list"))
        .containsExactly(11L, 22L);
  }

  @Test
  void theCommittedListStartsWithTheAbilityDraftFixtures() throws IOException {
    assertThat(RoleLoopMatchList.read(Path.of("src/test/resources/replay/role-loop/matches.txt")))
        .containsExactly(9038725154L, 9037038717L, 9040892342L, 9040889566L);
  }

  @Test
  void theFirstRoundIsOneAndTheNextFollowsTheLastExisting() {
    assertThat(RoleLoopRound.open(base, null, false).number()).isEqualTo(1);
    new RoleLoopRound(base, 3).write("a.md", "x");

    RoleLoopRound next = RoleLoopRound.open(base, null, false);

    assertThat(next.number()).isEqualTo(4);
    assertThat(next.dir().getFileName().toString()).isEqualTo("round-04");
    assertThat(RoleLoopRound.open(base, "2", false).number()).isEqualTo(2);
  }

  @Test
  void thePreviousReportIsFromTheLatestEarlierRoundThatHasIt() {
    new RoleLoopRound(base, 1).write("a.md", "one");
    new RoleLoopRound(base, 2).write("b.md", "other");

    RoleLoopRound third = new RoleLoopRound(base, 3);

    assertThat(third.previous("a.md", r -> true)).contains("one");
    assertThat(new RoleLoopRound(base, 1).previous("a.md", r -> true)).isEmpty();
  }

  @Test
  void aMalformedLineIsNamedWithItsFileAndNumber() {
    assertThatThrownBy(() -> RoleLoopMatchList.parse(List.of("11", "# ok", "12ab"), "matches.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matches.txt line 3: not a match id: '12ab'");
  }

  @Test
  void aBadRoundNumberIsExplained() {
    assertThatThrownBy(() -> RoleLoopRound.open(base, "two", false))
        .hasMessageContaining("ROUND must be a positive whole number, got 'two'");
    assertThatThrownBy(() -> RoleLoopRound.open(base, "0", false))
        .hasMessageContaining("positive whole number");
  }

  @Test
  void anExplicitRoundWithReportsIsNotOverwrittenUnlessAsked() {
    new RoleLoopRound(base, 2).write("a.md", "x");

    assertThatThrownBy(() -> RoleLoopRound.open(base, "2", false))
        .hasMessageContaining("ROUND_OVERWRITE=1");
    assertThat(RoleLoopRound.open(base, "2", true).number()).isEqualTo(2);
    assertThat(RoleLoopRound.open(base, "3", false).number()).isEqualTo(3);
  }

  @Test
  void aSkippedReportIsNotTheBaseline() {
    new RoleLoopRound(base, 1).write("a.md", "# Match 1\n\n## Roles\n");
    new RoleLoopRound(base, 2).write("a.md", RoleLoopReport.unavailable(1, "no fixture"));

    assertThat(new RoleLoopRound(base, 3).previous("a.md", r -> !RoleLoopReport.isUnavailable(r)))
        .contains("# Match 1\n\n## Roles\n");
  }
}
