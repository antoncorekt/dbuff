package com.ako.dbuff.service.replay.roleloop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLoopFilesTest {
  @TempDir Path base;

  @Test
  void theMatchListIgnoresCommentsBlanksAndRepeats() {
    assertThat(
            RoleLoopMatchList.parse(
                List.of("# Ability Draft", "", " 11 ", "22 # a note", "11", "  # x")))
        .containsExactly(11L, 22L);
  }

  @Test
  void theCommittedListStartsWithTheAbilityDraftFixtures() throws IOException {
    assertThat(RoleLoopMatchList.read(Path.of("src/test/resources/replay/role-loop/matches.txt")))
        .containsExactly(9038725154L, 9037038717L, 9040892342L, 9040889566L);
  }

  @Test
  void theFirstRoundIsOneAndTheNextFollowsTheLastExisting() {
    assertThat(RoleLoopRound.open(base, null).number()).isEqualTo(1);
    new RoleLoopRound(base, 3).write("a.md", "x");

    RoleLoopRound next = RoleLoopRound.open(base, null);

    assertThat(next.number()).isEqualTo(4);
    assertThat(next.dir().getFileName().toString()).isEqualTo("round-04");
    assertThat(RoleLoopRound.open(base, 2).number()).isEqualTo(2);
  }

  @Test
  void thePreviousReportIsFromTheLatestEarlierRoundThatHasIt() {
    new RoleLoopRound(base, 1).write("a.md", "one");
    new RoleLoopRound(base, 2).write("b.md", "other");

    RoleLoopRound third = new RoleLoopRound(base, 3);

    assertThat(third.previous("a.md")).contains("one");
    assertThat(new RoleLoopRound(base, 1).previous("a.md")).isEmpty();
  }
}
