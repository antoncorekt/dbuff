package com.ako.dbuff.config;

import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200ResponseOneOfInner;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads an OpenDota constants response: an object, or an array of objects or integers.
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
    JsonToken token = parser.currentToken();
    if (token == JsonToken.START_OBJECT) {
      return new GetConstantsByResource200Response(readMap(parser, context));
    }
    if (token == JsonToken.START_ARRAY) {
      return new GetConstantsByResource200Response(readArray(parser, context));
    }
    throw JsonMappingException.from(
        parser, "Constants response must be an object or an array, found " + token);
  }

  private static List<GetConstantsByResource200ResponseOneOfInner> readArray(
      JsonParser parser, DeserializationContext context) throws IOException {
    List<GetConstantsByResource200ResponseOneOfInner> items = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      JsonToken token = parser.currentToken();
      if (token == JsonToken.START_OBJECT) {
        items.add(new GetConstantsByResource200ResponseOneOfInner(readMap(parser, context)));
      } else if (token == JsonToken.VALUE_NUMBER_INT) {
        items.add(new GetConstantsByResource200ResponseOneOfInner(parser.getLongValue()));
      } else {
        throw JsonMappingException.from(
            parser,
            "Constants array element %d must be an object or an integer, found %s"
                .formatted(items.size(), token));
      }
    }
    return items;
  }

  private static Map<String, Object> readMap(JsonParser parser, DeserializationContext context)
      throws IOException {
    JavaType mapType =
        context.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    return context.readValue(parser, mapType);
  }
}
