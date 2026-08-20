package com.ako.dbuff.service.discord.command;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the test double itself, so later handler tests can trust it. */
class FakeCommandContextTest {

  @Test
  void acknowledge_recordsSummaryAndThreadName() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("Scouting Termit…", "stats: Termit");

    assertThat(context.getAcknowledgeSummary()).isEqualTo("Scouting Termit…");
    assertThat(context.getAcknowledgeThreadName()).isEqualTo("stats: Termit");
    assertThat(reply).isNotNull();
  }

  @Test
  void asyncReply_capturesPostsInOrder() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.post("first");
    reply.post("second");

    assertThat(context.getPosts()).containsExactly("first", "second");
  }

  @Test
  void asyncReply_capturesFailuresSeparatelyFromPosts() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.post("progress");
    reply.fail("boom");

    assertThat(context.getPosts()).containsExactly("progress");
    assertThat(context.getFailures()).containsExactly("boom");
  }

  @Test
  void replyEphemeral_isCapturedAndDoesNotCreateAThread() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    context.replyEphemeral("No instance registered.");

    assertThat(context.getEphemeralReplies()).containsExactly("No instance registered.");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void options_areReadableByName() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Termit")
            .option("period", "last_7_days")
            .build();

    assertThat(context.getOption("player")).isEqualTo("Termit");
    assertThat(context.getOption("period")).isEqualTo("last_7_days");
    assertThat(context.getOption("absent")).isNull();
  }

  @Test
  void getOptionAsList_splitsOnCommasAndTrims() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("items", "blink, black_king_bar ,manta").build();

    assertThat(context.getOptionAsList("items"))
        .containsExactly("blink", "black_king_bar", "manta");
  }

  @Test
  void getOptionAsList_absentOption_isEmptyList() {
    assertThat(FakeCommandContext.builder().build().getOptionAsList("items")).isEmpty();
  }

  @Test
  void getOptionAsList_blankEntriesAreDropped() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("items", "blink,, ,manta").build();

    assertThat(context.getOptionAsList("items")).containsExactly("blink", "manta");
  }

  @Test
  void getOptionAsInt_parsesOrReturnsDefault() {
    FakeCommandContext context = FakeCommandContext.builder().option("limit", "15").build();

    assertThat(context.getOptionAsInt("limit", 10)).isEqualTo(15);
    assertThat(context.getOptionAsInt("absent", 10)).isEqualTo(10);
  }

  @Test
  void getOptionAsInt_unparseableValue_returnsDefault() {
    FakeCommandContext context = FakeCommandContext.builder().option("limit", "abc").build();

    assertThat(context.getOptionAsInt("limit", 10)).isEqualTo(10);
  }

  @Test
  void threadName_alsoMarksTheContextAsInsideAThread() {
    FakeCommandContext context =
        FakeCommandContext.builder().threadName("Match 8795480597").build();

    assertThat(context.isInsideThread()).isTrue();
    assertThat(context.getThreadName()).contains("Match 8795480597");
  }

  @Test
  void insideThread_defaultsToFalse() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    assertThat(context.isInsideThread()).isFalse();
    assertThat(context.getThreadName()).isEmpty();
  }

  @Test
  void postLines_capturesEachLine() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.postLines(List.of("line one", "line two"));

    assertThat(context.getPosts()).containsExactly("line one", "line two");
  }
}
