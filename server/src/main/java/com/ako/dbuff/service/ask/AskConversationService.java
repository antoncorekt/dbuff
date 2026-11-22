package com.ako.dbuff.service.ask;

import com.ako.dbuff.dao.model.AskThreadDomain;
import com.ako.dbuff.dao.model.AskTurnDomain;
import com.ako.dbuff.dao.repo.AskThreadRepo;
import com.ako.dbuff.dao.repo.AskTurnRepo;
import com.ako.dbuff.service.ask.tool.AskToolRegistry;
import com.ako.dbuff.service.ask.tool.AskToolRegistry.ToolResult;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Runs the manual tool loop of one question and keeps the conversation of the thread. */
@Slf4j
@Service
public class AskConversationService {

  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
  static final String REFUSAL_TEXT = "I can't answer that one.";
  static final String CUT_SHORT_TEXT = "\n\n(answer cut short)";
  static final String BUDGET_ERROR = "tool budget reached, answer with what you have";

  private static final int EXTRA_ROUNDS_AFTER_BUDGET = 3;

  private final AskModel model;
  private final AskToolRegistry registry;
  private final AskThreadRepo threadRepo;
  private final AskTurnRepo turnRepo;
  private final AskHistoryCodec codec;
  private final AskProperties properties;
  private final String systemPrompt;

  public AskConversationService(
      AskModel model,
      AskToolRegistry registry,
      AskThreadRepo threadRepo,
      AskTurnRepo turnRepo,
      AskHistoryCodec codec,
      AskProperties properties) {
    this.model = model;
    this.registry = registry;
    this.threadRepo = threadRepo;
    this.turnRepo = turnRepo;
    this.codec = codec;
    this.properties = properties;
    this.systemPrompt = loadSystemPrompt();
  }

  /** Records a new thread; a thread must exist before its first question is answered. */
  public void openThread(
      String threadId, String instanceId, String channelId, String starterDiscordUserId) {
    Instant now = Instant.now();
    threadRepo.save(
        AskThreadDomain.builder()
            .threadId(threadId)
            .instanceId(instanceId)
            .channelId(channelId)
            .starterDiscordUserId(starterDiscordUserId)
            .createdAt(now)
            .lastActivityAt(now)
            .build());
  }

  public AskAnswer answer(String threadId, AskContext ctx, String question) {
    AskThreadDomain thread =
        threadRepo
            .findById(threadId)
            .orElseThrow(() -> new IllegalStateException("Unknown ask thread " + threadId));

    List<AskTurnDomain> stored = turnRepo.findByThreadIdOrderBySeqAsc(threadId);
    List<BetaMessageParam> history = new ArrayList<>();
    stored.forEach(turn -> history.add(codec.fromJson(turn.getContentJson())));
    int seq = stored.size();

    String userText = stored.isEmpty() ? ctx.toPromptBlock() + question : question;
    BetaMessageParam userMessage = BetaMessageParam.ofUser(userText);
    persist(threadId, seq++, userMessage, true, null);
    history.add(userMessage);

    int toolCalls = 0;
    int rounds = 0;
    long inputTokens = 0;
    long outputTokens = 0;
    long cacheReadTokens = 0;
    BetaMessage last;
    String stopReason;

    while (true) {
      last = model.create(buildParams(history));
      rounds++;
      stopReason = last.stopReason().map(r -> r.asString()).orElse("end_turn");
      inputTokens += last.usage().inputTokens();
      outputTokens += last.usage().outputTokens();
      cacheReadTokens += last.usage().cacheReadInputTokens().orElse(0L);

      if (!last.content().isEmpty()) {
        BetaMessageParam assistant = last.toParam();
        persist(threadId, seq++, assistant, false, last);
        history.add(assistant);
      }
      if (!"tool_use".equals(stopReason)) {
        break;
      }
      if (rounds > properties.getMaxToolCallsPerAnswer() + EXTRA_ROUNDS_AFTER_BUDGET) {
        stopReason = "tool_budget";
        break;
      }

      List<BetaContentBlockParam> results = new ArrayList<>();
      for (BetaContentBlock block : last.content()) {
        if (block.toolUse().isEmpty()) {
          continue;
        }
        BetaToolUseBlock use = block.toolUse().get();
        ToolResult result;
        if (toolCalls >= properties.getMaxToolCallsPerAnswer()) {
          result = new ToolResult(BUDGET_ERROR, true);
        } else {
          toolCalls++;
          JsonNode input = ObjectMappers.jsonMapper().valueToTree(use._input());
          result = registry.dispatch(use.name(), input, ctx);
        }
        results.add(
            BetaContentBlockParam.ofToolResult(
                BetaToolResultBlockParam.builder()
                    .toolUseId(use.id())
                    .content(result.content())
                    .isError(result.error())
                    .build()));
      }
      BetaMessageParam resultMessage = BetaMessageParam.ofUserOfContentBlockParams(results);
      persist(threadId, seq++, resultMessage, false, null);
      history.add(resultMessage);
    }

    thread.setLastActivityAt(Instant.now());
    threadRepo.save(thread);

    log.info(
        "Ask answered thread={} toolCalls={} inputTokens={} outputTokens={} cacheReadTokens={}"
            + " stopReason={}",
        threadId,
        toolCalls,
        inputTokens,
        outputTokens,
        cacheReadTokens,
        stopReason);

    return new AskAnswer(finalText(last, stopReason), stopReason, toolCalls);
  }

  MessageCreateParams buildParams(List<BetaMessageParam> history) {
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder()
            .model(properties.getModel())
            .maxTokens(properties.getMaxTokens())
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(BetaOutputConfig.Effort.of(properties.getEffort()))
                    .build())
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
            .cacheControl(BetaCacheControlEphemeral.builder().build())
            .system(systemPrompt)
            .messages(history);
    registry.sdkTools().forEach(builder::addTool);
    return builder.build();
  }

  private void persist(
      String threadId, int seq, BetaMessageParam param, boolean userQuestion, BetaMessage source) {
    turnRepo.save(
        AskTurnDomain.builder()
            .threadId(threadId)
            .seq(seq)
            .role(param.role().asString())
            .contentJson(codec.toJson(param))
            .userQuestion(userQuestion)
            .inputTokens(source == null ? 0 : source.usage().inputTokens())
            .outputTokens(source == null ? 0 : source.usage().outputTokens())
            .cacheReadTokens(source == null ? 0 : source.usage().cacheReadInputTokens().orElse(0L))
            .createdAt(Instant.now())
            .build());
  }

  private static String finalText(BetaMessage message, String stopReason) {
    return switch (stopReason) {
      case "refusal" -> REFUSAL_TEXT;
      case "max_tokens" -> text(message) + CUT_SHORT_TEXT;
      default -> text(message);
    };
  }

  private static String text(BetaMessage message) {
    StringBuilder text = new StringBuilder();
    message.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
    return text.toString();
  }

  private static String loadSystemPrompt() {
    try (var in = new ClassPathResource("ask/system-prompt.md").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load ask/system-prompt.md", e);
    }
  }
}
