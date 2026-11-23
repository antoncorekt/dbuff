package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.ask.AskProperties;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Collects the {@link AskTool} beans, describes them to the model and runs its calls. */
@Slf4j
@Component
public class AskToolRegistry {

  static final String TRUNCATED = "…[truncated]";

  /** The outcome of one tool call as it goes back to the model. */
  public record ToolResult(String content, boolean error) {}

  private final Map<String, AskTool> tools = new TreeMap<>();
  private final ObjectMapper mapper;
  private final AskProperties properties;

  public AskToolRegistry(List<AskTool> beans, ObjectMapper mapper, AskProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
    for (AskTool tool : beans) {
      if (tools.put(tool.name(), tool) != null) {
        throw new IllegalStateException("Duplicate ask tool name: " + tool.name());
      }
    }
  }

  /** The tools by name, strict, so the request prefix is the same on every call. */
  public List<BetaTool> sdkTools() {
    return tools.values().stream()
        .sorted(Comparator.comparing(AskTool::name))
        .map(AskToolRegistry::toSdkTool)
        .toList();
  }

  public ToolResult dispatch(String name, JsonNode input, AskContext ctx) {
    AskTool tool = tools.get(name);
    if (tool == null) {
      return new ToolResult("Unknown tool: " + name, true);
    }
    log.debug("Ask tool {} input {}", name, input);
    try {
      return new ToolResult(truncate(mapper.writeValueAsString(tool.call(input, ctx))), false);
    } catch (AskToolException | IllegalArgumentException e) {
      return new ToolResult(e.getMessage(), true);
    } catch (Exception e) {
      log.warn("Ask tool {} failed", name, e);
      return new ToolResult("Tool " + name + " failed: " + e.getClass().getSimpleName(), true);
    }
  }

  private String truncate(String json) {
    int max = properties.getToolResultMaxChars();
    return json.length() <= max ? json : json.substring(0, max) + TRUNCATED;
  }

  @SuppressWarnings("unchecked")
  private static BetaTool toSdkTool(AskTool tool) {
    Map<String, Object> schema = new HashMap<>(tool.inputSchema());
    Map<String, Object> properties = (Map<String, Object>) schema.remove("properties");
    List<String> required = (List<String>) schema.remove("required");
    schema.remove("type");

    BetaTool.InputSchema.Properties.Builder props = BetaTool.InputSchema.Properties.builder();
    if (properties != null) {
      properties.forEach((key, value) -> props.putAdditionalProperty(key, JsonValue.from(value)));
    }
    BetaTool.InputSchema.Builder input = BetaTool.InputSchema.builder().properties(props.build());
    if (required != null) {
      input.required(required);
    }
    schema.forEach((key, value) -> input.putAdditionalProperty(key, JsonValue.from(value)));
    input.putAdditionalProperty("additionalProperties", JsonValue.from(false));

    return BetaTool.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(input.build())
        .strict(true)
        .build();
  }
}
