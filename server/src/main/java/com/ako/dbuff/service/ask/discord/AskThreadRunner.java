package com.ako.dbuff.service.ask.discord;

import com.ako.dbuff.service.ask.AskAnswer;
import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.ask.AskConversationService;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.discord.command.adapter.DiscordNames;
import com.ako.dbuff.service.discord.command.adapter.ThreadAsyncReply;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Component;

/** Runs one ask question in its thread: opens the thread, keeps typing, posts the answer. */
@Slf4j
@Component
public class AskThreadRunner {

  static final int THREAD_NAME_SOURCE_LENGTH = 80;
  static final long TYPING_INTERVAL_SECONDS = 8;

  /**
   * One question with everything the runner needs.
   *
   * @param instanceId the dbuff instance of the channel
   * @param channelId the parent text channel
   * @param asker the asking focus-group player
   * @param starterDiscordUserId the Discord user who asked
   * @param focusGroup the tracked players of the channel
   * @param trackedGameModes the game modes tracked by the channel
   * @param question the question text
   */
  public record AskRequest(
      String instanceId,
      String channelId,
      ResolvedPlayer asker,
      String starterDiscordUserId,
      List<ResolvedPlayer> focusGroup,
      Set<String> trackedGameModes,
      String question) {}

  private static final class Lease {
    final ReentrantLock lock = new ReentrantLock(true);
    int holders;
  }

  private final AskConversationService conversation;
  private final Map<String, Lease> leases = new ConcurrentHashMap<>();
  private final ScheduledExecutorService typing =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "ask-typing");
            thread.setDaemon(true);
            return thread;
          });

  public AskThreadRunner(AskConversationService conversation) {
    this.conversation = conversation;
  }

  @PreDestroy
  void shutdown() {
    typing.shutdownNow();
  }

  /** Opens a thread on the message, records it, and answers the first question in it. */
  public void startThread(Message message, AskRequest request) {
    ThreadChannel thread;
    try {
      String name =
          DiscordNames.sanitizeThreadName(
              abbreviate(request.question(), THREAD_NAME_SOURCE_LENGTH));
      thread =
          message
              .createThreadChannel(name)
              .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
              .complete();
      conversation.openThread(
          thread.getId(),
          request.instanceId(),
          request.channelId(),
          request.starterDiscordUserId());
    } catch (Exception e) {
      log.error("Ask could not open a thread in channel {}", request.channelId(), e);
      message.reply("❌ Couldn't answer: " + describe(e)).queue();
      return;
    }
    run(thread, request);
  }

  /** Answers a follow-up in an existing ask thread. */
  public void followUp(ThreadChannel thread, AskRequest request) {
    run(thread, request);
  }

  private void run(ThreadChannel thread, AskRequest request) {
    String threadId = thread.getId();
    Lease lease = acquire(threadId);
    lease.lock.lock();
    ScheduledFuture<?> typingTask = null;
    try {
      typingTask = startTyping(thread);
      AskContext context =
          new AskContext(
              request.instanceId(),
              request.channelId(),
              request.focusGroup(),
              request.asker(),
              LocalDate.now(ZoneOffset.UTC),
              request.trackedGameModes());
      AskAnswer answer = conversation.answer(threadId, context, request.question());
      post(thread, answer.text());
    } catch (Exception e) {
      log.error("Ask failed in thread {}", threadId, e);
      try {
        post(thread, "❌ Couldn't answer: " + describe(e));
      } catch (Exception postFailure) {
        log.error("Ask could not post its error to thread {}", threadId, postFailure);
      }
    } finally {
      if (typingTask != null) {
        typingTask.cancel(false);
      }
      lease.lock.unlock();
      release(threadId);
    }
  }

  private ScheduledFuture<?> startTyping(ThreadChannel thread) {
    return typing.scheduleWithFixedDelay(
        () -> {
          try {
            thread.sendTyping().queue();
          } catch (Exception e) {
            log.debug("Typing indicator failed in thread {}", thread.getId(), e);
          }
        },
        0,
        TYPING_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  private static void post(ThreadChannel thread, String text) {
    String body = text == null || text.isBlank() ? "(no answer)" : text;
    for (String chunk : ThreadAsyncReply.splitToLimit(body)) {
      thread.sendMessage(chunk).complete();
    }
  }

  private Lease acquire(String threadId) {
    return leases.compute(
        threadId,
        (id, lease) -> {
          Lease current = lease == null ? new Lease() : lease;
          current.holders++;
          return current;
        });
  }

  private void release(String threadId) {
    leases.computeIfPresent(threadId, (id, lease) -> --lease.holders == 0 ? null : lease);
  }

  private static String describe(Exception e) {
    return e.getMessage() == null ? "unexpected error" : e.getMessage();
  }

  private static String abbreviate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }
}
