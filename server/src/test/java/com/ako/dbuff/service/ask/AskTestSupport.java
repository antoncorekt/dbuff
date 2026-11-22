package com.ako.dbuff.service.ask;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaThinkingBlock;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

public final class AskTestSupport {

  private AskTestSupport() {}

  public static BetaContentBlock text(String text) {
    return BetaContentBlock.ofText(BetaTextBlock.builder().text(text).citations(List.of()).build());
  }

  public static BetaContentBlock thinking(String thinking) {
    return BetaContentBlock.ofThinking(
        BetaThinkingBlock.builder().thinking(thinking).signature("sig-" + thinking).build());
  }

  public static BetaContentBlock toolUse(String id, String name, Map<String, Object> input) {
    return BetaContentBlock.ofToolUse(
        BetaToolUseBlock.builder().id(id).name(name).input(JsonValue.from(input)).build());
  }

  public static BetaMessage message(BetaStopReason stop, BetaContentBlock... blocks) {
    ObjectMapper mapper = ObjectMappers.jsonMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("id", "msg_1");
    node.put("type", "message");
    node.put("role", "assistant");
    node.put("model", "claude-sonnet-5-5");
    node.set("content", mapper.valueToTree(List.of(blocks)));
    node.put("stop_reason", stop.asString());
    node.set("usage", mapper.valueToTree(usage()));
    for (String nullable :
        List.of(
            "container",
            "context_management",
            "diagnostics",
            "stop_details",
            "stop_sequence",
            "input_transformations")) {
      node.putNull(nullable);
    }
    try {
      return mapper.treeToValue(node, BetaMessage.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BetaUsage usage() {
    try {
      return ObjectMappers.jsonMapper()
          .readValue(
              "{\"input_tokens\":10,\"output_tokens\":5,\"cache_read_input_tokens\":0,"
                  + "\"cache_creation_input_tokens\":0,\"cache_creation\":null,"
                  + "\"fallback_credit\":null,\"inference_geo\":null,\"iterations\":null,"
                  + "\"output_tokens_details\":null,\"server_tool_use\":null,"
                  + "\"service_tier\":null,\"speed\":null}",
              BetaUsage.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
