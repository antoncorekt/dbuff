package com.ako.dbuff.service.ask.discord;

import com.ako.dbuff.service.ask.AskAnswer;
import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.ask.AskConversationService;
import com.ako.dbuff.service.ask.discord.AskThreadRunner.AskRequest;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskThreadRunnerTest {

  private final AskConversationService conversation = Mockito.mock(AskConversationService.class);
  private final AskThreadRunner runner = new AskThreadRunner(conversation);
  private final ResolvedPlayer asker = new ResolvedPlayer(1L, "Anton");

  private AskRequest request(String question) {
    return new AskRequest("inst", "chan", asker, "discord-1", List.of(asker), Set.of(), question);
  }

  private static ThreadChannel thread(String id) {
    ThreadChannel thread = Mockito.mock(ThreadChannel.class, Answers.RETURNS_DEEP_STUBS);
    when(thread.getId()).thenReturn(id);
    return thread;
  }

  @Test
  void aNewQuestion_createsAThreadSavesTheRowAndPostsTheAnswer() {
    Message message = Mockito.mock(Message.class, Answers.RETURNS_DEEP_STUBS);
    ThreadChannel thread = thread("t1");
    when(message
            .createThreadChannel(anyString())
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
            .complete())
        .thenReturn(thread);
    when(conversation.answer(eq("t1"), any(AskContext.class), eq("who is best?")))
        .thenReturn(new AskAnswer("Anton", "end_turn", 0));

    runner.startThread(message, request("who is best?"));

    verify(conversation).openThread("t1", "inst", "chan", "discord-1");
    verify(thread).sendMessage("Anton");
  }

  @Test
  void twoFollowUpsOnOneThread_runOneAfterTheOther() throws Exception {
    ThreadChannel thread = thread("t2");
    AtomicInteger running = new AtomicInteger();
    AtomicInteger maxRunning = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(2);
    when(conversation.answer(eq("t2"), any(AskContext.class), anyString()))
        .thenAnswer(
            invocation -> {
              maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
              Thread.sleep(150);
              running.decrementAndGet();
              return new AskAnswer("ok", "end_turn", 0);
            });

    for (String question : List.of("one", "two")) {
      Thread.startVirtualThread(
          () -> {
            runner.followUp(thread, request(question));
            done.countDown();
          });
    }

    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(maxRunning.get()).isEqualTo(1);
  }

  @Test
  void anException_postsTheErrorReply() {
    ThreadChannel thread = thread("t3");
    when(conversation.answer(eq("t3"), any(AskContext.class), anyString()))
        .thenThrow(new IllegalStateException("model down"));

    runner.followUp(thread, request("anything"));

    verify(thread).sendMessage(contains("❌ Couldn't answer: model down"));
  }
}
