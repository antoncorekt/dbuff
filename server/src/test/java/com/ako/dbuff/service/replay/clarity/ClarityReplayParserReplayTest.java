package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parses a real Ability Draft replay; runs only through {@code :server:replayTest}. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class ClarityReplayParserReplayTest {

  private static final String MATCH = "9038725154.dem";

  private final ClarityReplayParser parser = new ClarityReplayParser();

  @Test
  void parse_readsTheClockStartAndTheRosterOfARealReplay() {
    ReplayFacts facts = parser.parse(replay(MATCH), () -> false);

    assertThat(facts.clockStartTick()).isEqualTo(17136);
    assertThat(facts.gameStartTime()).isBetween(596.0, 597.0);
    List<ReplayPlayerFact> players = facts.players();
    assertThat(players).hasSize(10);
    assertThat(players)
        .extracting(ReplayPlayerFact::playerSlot)
        .containsExactly(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L, 131L, 132L);
    assertThat(players.subList(0, 5)).allMatch(p -> p.teamNum() == 2);
    assertThat(players.subList(5, 10)).allMatch(p -> p.teamNum() == 3);
    assertThat(players).allMatch(p -> p.heroEntityName() != null && !p.heroEntityName().isBlank());
  }

  @Test
  void parse_stopsWhenCancelled() {
    assertThatThrownBy(() -> parser.parse(replay(MATCH), () -> true))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_RESOURCE));
  }

  @Test
  void parse_reportsAFileThatIsNotAReplayAsAParserFailure(
      @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Path garbage = dir.resolve("garbage.dem");
    Files.writeString(garbage, "this is not a replay".repeat(100));

    assertThatThrownBy(() -> parser.parse(garbage, () -> false))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_PARSER));
  }

  private static Path replay(String name) {
    return Path.of(System.getenv("REPLAY_FIXTURE_DIR")).resolve(name);
  }
}
