package com.ako.dbuff.service.ask;

import com.ako.dbuff.dao.model.AskTurnDomain;
import com.ako.dbuff.dao.repo.AskThreadRepo;
import com.ako.dbuff.dao.repo.AskTurnRepo;
import com.ako.dbuff.service.ask.tool.AskTool;
import com.ako.dbuff.service.ask.tool.AskToolException;
import com.ako.dbuff.service.ask.tool.AskToolRegistry;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static com.ako.dbuff.service.ask.AskTestSupport.message;
import static com.ako.dbuff.service.ask.AskTestSupport.text;
import static com.ako.dbuff.service.ask.AskTestSupport.thinking;
import static com.ako.dbuff.service.ask.AskTestSupport.toolUse;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AskConversationServiceTest {

  private static final AskContext CTX =
      new AskContext(
          "inst",
          "chan",
          List.of(new ResolvedPlayer(1L, "Ann")),
          new ResolvedPlayer(1L, "Ann"),
          LocalDate.of(2026, 11, 22),
          Set.of("Ability Draft"));

  @Autowired private AskThreadRepo threadRepo;
  @Autowired private AskTurnRepo turnRepo;

  private final Deque<BetaMessage> responses = new ArrayDeque<>();
  private final List<MessageCreateParams> requests = new ArrayList<>();
  private final List<String> executed = new ArrayList<>();
  private final AskProperties properties = new AskProperties();
  private AskConversationService service;

  @BeforeEach
  void setUp() {
    AskModel model =
        new AskModel() {
          @Override
          public BetaMessage create(MessageCreateParams params) {
            requests.add(params);
            return responses.removeFirst();
          }

          @Override
          public boolean available() {
            return true;
          }
        };
    AskTool echo = tool("echo", (input) -> Map.of("echo", input.path("q").asText()));
    AskTool failing =
        tool(
            "failing",
            input -> {
              throw new AskToolException("Unknown player: Zed. Valid players: Ann");
            });
    AskToolRegistry registry =
        new AskToolRegistry(List.of(echo, failing), new ObjectMapper(), properties);
    service =
        new AskConversationService(
            model, registry, threadRepo, turnRepo, new AskHistoryCodec(), properties);
    service.openThread("thread-1", "inst", "chan", "discord-user");
  }

  @Test
  void parallelToolUsesAreAnsweredInOneUserMessage() {
    responses.add(
        message(
            BetaStopReason.TOOL_USE,
            thinking("hmm"),
            toolUse("t1", "echo", Map.of("q", "a")),
            toolUse("t2", "echo", Map.of("q", "b"))));
    responses.add(message(BetaStopReason.END_TURN, text("done")));

    AskAnswer answer = service.answer("thread-1", CTX, "how many?");

    assertThat(answer.text()).isEqualTo("done");
    assertThat(answer.toolCalls()).isEqualTo(2);
    assertThat(executed).containsExactly("a", "b");
    List<BetaMessageParam> second = requests.get(1).messages();
    assertThat(second).hasSize(3);
    List<BetaContentBlockParam> results =
        second.get(2).user().get().content().contentBlockParams().get();
    assertThat(results).hasSize(2);
    assertThat(results.get(0).toolResult().get().toolUseId()).isEqualTo("t1");
    assertThat(results.get(1).toolResult().get().toolUseId()).isEqualTo("t2");
    assertThat(results.get(0).toolResult().get().isError()).contains(false);
    assertThat(turnRepo.findByThreadIdOrderBySeqAsc("thread-1"))
        .extracting(AskTurnDomain::getRole, AskTurnDomain::isUserQuestion)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("user", true),
            org.assertj.core.groups.Tuple.tuple("assistant", false),
            org.assertj.core.groups.Tuple.tuple("user", false),
            org.assertj.core.groups.Tuple.tuple("assistant", false));
  }

  @Test
  void toolErrorIsReportedWithIsError() {
    responses.add(message(BetaStopReason.TOOL_USE, toolUse("t1", "failing", Map.of())));
    responses.add(message(BetaStopReason.END_TURN, text("sorry")));

    service.answer("thread-1", CTX, "q");

    var result =
        requests
            .get(1)
            .messages()
            .get(2)
            .user()
            .get()
            .content()
            .contentBlockParams()
            .get()
            .get(0)
            .toolResult()
            .get();
    assertThat(result.isError()).contains(true);
    assertThat(result.content().get().string().get()).contains("Valid players: Ann");
  }

  @Test
  void toolBudgetTurnsFurtherCallsIntoErrors() {
    properties.setMaxToolCallsPerAnswer(1);
    responses.add(
        message(
            BetaStopReason.TOOL_USE,
            toolUse("t1", "echo", Map.of("q", "a")),
            toolUse("t2", "echo", Map.of("q", "b"))));
    responses.add(message(BetaStopReason.END_TURN, text("partial")));

    AskAnswer answer = service.answer("thread-1", CTX, "q");

    assertThat(answer.toolCalls()).isEqualTo(1);
    assertThat(executed).containsExactly("a");
    var results =
        requests.get(1).messages().get(2).user().get().content().contentBlockParams().get();
    assertThat(results.get(0).toolResult().get().isError()).contains(false);
    assertThat(results.get(1).toolResult().get().isError()).contains(true);
    assertThat(results.get(1).toolResult().get().content().get().string().get())
        .isEqualTo(AskConversationService.BUDGET_ERROR);
  }

  @Test
  void refusalGivesTheFixedText() {
    responses.add(message(BetaStopReason.REFUSAL));

    AskAnswer answer = service.answer("thread-1", CTX, "q");

    assertThat(answer.text()).isEqualTo("I can't answer that one.");
    assertThat(answer.stopReason()).isEqualTo("refusal");
  }

  @Test
  void maxTokensKeepsTheTextWithANote() {
    responses.add(message(BetaStopReason.MAX_TOKENS, text("half an answ")));

    AskAnswer answer = service.answer("thread-1", CTX, "q");

    assertThat(answer.text()).startsWith("half an answ").endsWith("(answer cut short)");
  }

  @Test
  void historyIsReplayedUnchangedOnTheSecondQuestion() throws Exception {
    BetaMessage first = message(BetaStopReason.END_TURN, thinking("deep"), text("first answer"));
    responses.add(first);
    responses.add(message(BetaStopReason.END_TURN, text("second answer")));

    service.answer("thread-1", CTX, "first question");
    AskAnswer second = service.answer("thread-1", CTX, "second question");

    assertThat(second.text()).isEqualTo("second answer");
    List<BetaMessageParam> replay = requests.get(1).messages();
    assertThat(replay).hasSize(3);
    assertThat(replay.get(1)).isEqualTo(first.toParam());
    assertThat(replay.get(0).user().get().content().string().get())
        .contains("<context>")
        .contains("Ann (1)")
        .contains("Ability Draft")
        .endsWith("first question");
    assertThat(replay.get(2).user().get().content().string().get()).isEqualTo("second question");
  }

  @Test
  void requestCarriesTheFixedSettings() throws Exception {
    responses.add(message(BetaStopReason.END_TURN, text("ok")));

    service.answer("thread-1", CTX, "q");

    MessageCreateParams params = requests.get(0);
    JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());
    assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5-5");
    assertThat(body.path("max_tokens").asInt()).isEqualTo(16000);
    assertThat(body.path("output_config").path("effort").asText()).isEqualTo("medium");
    assertThat(body.path("cache_control").path("type").asText()).isEqualTo("ephemeral");
    assertThat(body.path("fallbacks").asText()).isEqualTo("default");
    assertThat(body.has("temperature")).isFalse();
    assertThat(body.has("thinking")).isFalse();
    assertThat(body.has("tool_choice")).isFalse();
    assertThat(body.path("system").asText()).contains("dbuff");
    assertThat(body.path("tools")).hasSize(2);
    assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("echo");
    assertThat(body.path("tools").get(0).path("strict").asBoolean()).isTrue();
    assertThat(params._headers().values("anthropic-beta"))
        .contains("server-side-fallback-2026-07-01");
  }

  private AskTool tool(String name, java.util.function.Function<JsonNode, Object> body) {
    return new AskTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "test tool";
      }

      @Override
      public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("q", Map.of("type", "string")),
            "required", List.of());
      }

      @Override
      public Object call(JsonNode input, AskContext ctx) {
        executed.add(input.path("q").asText());
        return body.apply(input);
      }
    };
  }
}
