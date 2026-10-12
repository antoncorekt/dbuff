package com.ako.dbuff.config;

import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200ResponseOneOfInner;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads an OpenDota constants response: an object, or an array of objects or numbers.
 *
 * <p>The generated oneOf deserializers only try their {@code Map} and {@code Long} variants when
 * scalar coercion is enabled, and the generated client turns it off, so none of them matches. This
 * one decides by the JSON shape and leaves the mapper settings, and so every other call, alone.
 */
public class ConstantsResponseDeserializer
    extends StdDeserializer<GetConstantsByResource200Response> {

  public ConstantsResponseDeserializer() {
    super(GetConstantsByResource200Response.class);
  }

  @Override
  public GetConstantsByResource200Response deserialize(
      JsonParser parser, DeserializationContext context) throws IOException {
    JsonNode tree = parser.readValueAsTree();
    if (tree.isObject()) {
      return new GetConstantsByResource200Response(
          parser
              .getCodec()
              .readValue(
                  tree.traverse(parser.getCodec()), new TypeReference<Map<String, Object>>() {}));
    }
    if (tree.isArray()) {
      List<GetConstantsByResource200ResponseOneOfInner> items = new ArrayList<>();
      for (JsonNode element : tree) {
        items.add(toInner(parser, element));
      }
      return new GetConstantsByResource200Response(items);
    }
    return (GetConstantsByResource200Response)
        context.handleUnexpectedToken(GetConstantsByResource200Response.class, parser);
  }

  private static GetConstantsByResource200ResponseOneOfInner toInner(
      JsonParser parser, JsonNode element) throws IOException {
    if (element.isIntegralNumber()) {
      return new GetConstantsByResource200ResponseOneOfInner(element.longValue());
    }
    return new GetConstantsByResource200ResponseOneOfInner(
        parser
            .getCodec()
            .readValue(
                element.traverse(parser.getCodec()), new TypeReference<Map<String, Object>>() {}));
  }
}
