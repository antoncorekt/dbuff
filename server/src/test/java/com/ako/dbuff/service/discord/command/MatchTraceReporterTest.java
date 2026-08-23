package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.resources.model.MatchReference;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTraceReporterTest {

  private static final Long TIGRESS = 201613150L;

  private PlayerStatisticService playerStatisticService;
  private MatchTraceReporter reporter;

  @BeforeEach
  void setUp() {
    playerStatisticService = Mockito.mock(PlayerStatisticService.class);
    reporter = new MatchTraceReporter(playerStatisticService);
  }

  private static StatsRequestResolver.StatsRequest request() {
    return new StatsRequestResolver.StatsRequest(
        List.of(new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress")),
        Set.of(),
        Set.of("game_mode_ability_draft"),
        LocalDate.of(2026, 7, 20),
        LocalDate.of(2026, 8, 19),
        "Last 30 days",
        "Ability Draft",
        0);
  }

  private static List<MatchReference> matches(int count) {
    List<MatchReference> matches = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      matches.add(new MatchReference(8795480597L + i, LocalDate.of(2026, 8, 10)));
    }
    return matches;
  }

  private void returns(List<MatchReference> matches) {
    Mockito.when(
            playerStatisticService.getPlayerMatches(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyInt()))
        .thenReturn(matches);
  }

  // ----------------------------------------------------------------- formatting

  @Test
  void listsEachMatchAsIdThenDate() {
    String message =
        reporter.format(
            "Tigress",
            List.of(
                new MatchReference(8795480597L, LocalDate.of(2026, 8, 10)),
                new MatchReference(8795480598L, LocalDate.of(2026, 8, 9))));

    assertThat(message).contains("8795480597 - 2026-08-10");
    assertThat(message).contains("8795480598 - 2026-08-09");
    assertThat(message).contains("Tigress");
  }

  /** A code block so Discord neither linkifies the IDs nor reflows the columns. */
  @Test
  void wrapsTheListInACodeBlock() {
    String message = reporter.format("Tigress", matches(2));

    assertThat(message).contains("```");
  }

  /** "No data" and "no matches" are different answers; the second must be stated, not implied. */
  @Test
  void noMatches_saysSoRatherThanPostingAnEmptyBlock() {
    String message = reporter.format("Tigress", List.of());

    assertThat(message).contains("No matches").contains("Tigress");
    assertThat(message).doesNotContain("```");
  }

  @Test
  void aMatchWithNoRecordedDate_saysSoRatherThanShowingNull() {
    String message = reporter.format("Tigress", List.of(new MatchReference(8795480597L, null)));

    assertThat(message).contains("8795480597 - " + MatchTraceReporter.UNKNOWN_DATE);
    assertThat(message).doesNotContain("null");
  }

  // ------------------------------------------------------------------ the cap

  @Test
  void withinTheCap_listsEverythingAndSaysNothingAboutTrimming() {
    String message = reporter.format("Tigress", matches(MatchTraceReporter.MAX_TRACED_MATCHES));

    assertThat(message.lines().filter(line -> line.contains(" - ")).count())
        .isEqualTo(MatchTraceReporter.MAX_TRACED_MATCHES);
    assertThat(message).doesNotContain("only — narrow the period");
  }

  /**
   * The cap is the last ten games. Dropping the tail is fine; dropping it quietly is not, because a
   * partial list reads exactly like a complete one.
   */
  @Test
  void beyondTheCap_trimsAndSaysWhatWasDropped() {
    String message = reporter.format("Tigress", matches(MatchTraceReporter.MAX_TRACED_MATCHES + 1));

    assertThat(message.lines().filter(line -> line.contains(" - ")).count())
        .isEqualTo(MatchTraceReporter.MAX_TRACED_MATCHES);
    assertThat(message).contains("Last 10 only");
  }

  // -------------------------------------------------------------------- posting

  @Test
  void forwardsTheRequestFiltersSoTheListMatchesTheNumbers() {
    returns(matches(1));
    RecordingReply reply = new RecordingReply();

    reporter.post(
        reply, request(), new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"), null);

    Mockito.verify(playerStatisticService)
        .getPlayerMatches(
            Mockito.eq(TIGRESS),
            Mockito.eq(LocalDate.of(2026, 7, 20)),
            Mockito.eq(LocalDate.of(2026, 8, 19)),
            Mockito.eq(Set.of()),
            Mockito.eq(Set.of("game_mode_ability_draft")),
            Mockito.isNull(),
            Mockito.anyInt());
    assertThat(reply.posts).hasSize(1);
  }

  /** The combo queries already know their games, so the trace must use that set verbatim. */
  @Test
  void forwardsAComboMatchSetWhenGivenOne() {
    returns(matches(1));
    RecordingReply reply = new RecordingReply();

    reporter.post(
        reply,
        request(),
        new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"),
        Set.of(1L, 2L));

    Mockito.verify(playerStatisticService)
        .getPlayerMatches(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(Set.of(1L, 2L)),
            Mockito.anyInt());
  }

  /** It asks for one past the cap, which is how it knows there is more without a second query. */
  @Test
  void asksForOneMoreThanTheCapToDetectTruncation() {
    returns(matches(1));
    RecordingReply reply = new RecordingReply();

    reporter.post(
        reply, request(), new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"), null);

    Mockito.verify(playerStatisticService)
        .getPlayerMatches(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(MatchTraceReporter.MAX_TRACED_MATCHES + 1));
  }

  /**
   * The trace is an extra on top of statistics that have already been posted, so a failure here
   * must be reported without discarding the answer the user came for.
   */
  @Test
  void queryFailure_isReportedIntoTheThreadRatherThanThrown() {
    Mockito.when(
            playerStatisticService.getPlayerMatches(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyInt()))
        .thenThrow(new IllegalStateException("database went away"));
    RecordingReply reply = new RecordingReply();

    reporter.post(
        reply, request(), new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"), null);

    assertThat(reply.posts).hasSize(1);
    assertThat(reply.posts.get(0)).contains("Could not list matches").contains("Tigress");
  }

  /** Captures what was posted without needing a Discord channel. */
  private static class RecordingReply implements AsyncReply {
    private final List<String> posts = new ArrayList<>();

    @Override
    public void post(String message) {
      posts.add(message);
    }

    @Override
    public void postEmbed(net.dv8tion.jda.api.entities.MessageEmbed embed) {
      throw new AssertionError("The trace posts plain messages, never embeds");
    }

    @Override
    public void fail(String message) {
      throw new AssertionError("The trace must not fail the command: " + message);
    }
  }
}
