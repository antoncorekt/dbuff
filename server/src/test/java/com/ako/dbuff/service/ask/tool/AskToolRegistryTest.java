package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.ask.AskProperties;
import com.ako.dbuff.service.ask.tool.AskToolRegistry.ToolResult;
import com.ako.dbuff.service.ranking.UnknownConstantNameException;
import com.anthropic.models.beta.messages.BetaTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskToolRegistryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AskContext CTX =
      new AskContext("i", "c", List.of(), null, LocalDate.of(2026, 11, 22), Set.of());

  private final AskProperties properties = new AskProperties();

  private AskToolRegistry registry(AskTool... tools) {
    return new AskToolRegistry(List.of(tools), MAPPER, properties);
  }

  @Test
  void toolsAreSortedStrictAndClosed() {
    List<BetaTool> tools =
        registry(tool("zeta", (i, c) -> 1), tool("alpha", (i, c) -> 1)).sdkTools();

    assertThat(tools).extracting(BetaTool::name).containsExactly("alpha", "zeta");
    BetaTool alpha = tools.get(0);
    assertThat(alpha.strict()).contains(true);
    assertThat(alpha.inputSchema().required()).contains(List.of("n"));
    assertThat(alpha.inputSchema()._additionalProperties()).containsKey("additionalProperties");
    assertThat(alpha.inputSchema()._additionalProperties().get("additionalProperties").toString())
        .contains("false");
  }

  @Test
  void duplicateNameIsRejected() {
    assertThatThrownBy(() -> registry(tool("a", (i, c) -> 1), tool("a", (i, c) -> 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("a");
  }

  @Test
  void resultIsSerializedAndTruncated() {
    properties.setToolResultMaxChars(10);
    AskToolRegistry registry = registry(tool("t", (i, c) -> Map.of("text", "x".repeat(100))));

    ToolResult result = registry.dispatch("t", MAPPER.createObjectNode(), CTX);

    assertThat(result.error()).isFalse();
    assertThat(result.content()).hasSize(10 + AskToolRegistry.TRUNCATED.length());
    assertThat(result.content()).endsWith(AskToolRegistry.TRUNCATED);
  }

  @Test
  void shortResultIsKept() {
    ToolResult result =
        registry(tool("t", (i, c) -> Map.of("a", 1))).dispatch("t", MAPPER.createObjectNode(), CTX);

    assertThat(result).isEqualTo(new ToolResult("{\"a\":1}", false));
  }

  @Test
  void errorsBecomeErrorResults() {
    AskToolRegistry registry =
        registry(
            tool(
                "ask",
                (i, c) -> {
                  throw new AskToolException("no such player");
                }),
            tool(
                "const",
                (i, c) -> {
                  throw new UnknownConstantNameException("hero", Set.of("x"));
                }),
            tool(
                "arg",
                (i, c) -> {
                  throw new IllegalArgumentException("bad arg");
                }),
            tool(
                "boom",
                (i, c) -> {
                  throw new IllegalStateException("secret");
                }));
    JsonNode input = MAPPER.createObjectNode();

    assertThat(registry.dispatch("ask", input, CTX))
        .isEqualTo(new ToolResult("no such player", true));
    assertThat(registry.dispatch("const", input, CTX).error()).isTrue();
    assertThat(registry.dispatch("const", input, CTX).content()).contains("x");
    assertThat(registry.dispatch("arg", input, CTX)).isEqualTo(new ToolResult("bad arg", true));
    ToolResult boom = registry.dispatch("boom", input, CTX);
    assertThat(boom.error()).isTrue();
    assertThat(boom.content()).doesNotContain("secret");
  }

  @Test
  void unknownToolIsAnErrorResult() {
    ToolResult result = registry().dispatch("nope", MAPPER.createObjectNode(), CTX);

    assertThat(result.error()).isTrue();
    assertThat(result.content()).contains("nope");
  }

  private static AskTool tool(String name, BiFunction<JsonNode, AskContext, Object> body) {
    return new AskTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "d";
      }

      @Override
      public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("n", Map.of("type", "integer")),
            "required", List.of("n"));
      }

      @Override
      public Object call(JsonNode input, AskContext ctx) {
        return body.apply(input, ctx);
      }
    };
  }
}
