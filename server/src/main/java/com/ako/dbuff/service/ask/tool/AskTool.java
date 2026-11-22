package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** A tool the model can call. Adding one means adding a bean; the registry picks it up. */
public interface AskTool {

  String name();

  String description();

  /** JSON Schema of the input: {@code type}, {@code properties}, {@code required}. */
  Map<String, Object> inputSchema();

  /** The result, serialized to JSON by the registry. */
  Object call(JsonNode input, AskContext ctx);
}
