package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeroesAbilityConstant implements ConstantData {

  @JsonDeserialize(using = FlatStringListDeserializer.class)
  List<String> abilities;

  static class FlatStringListDeserializer extends JsonDeserializer<List<String>> {
    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      List<String> result = new ArrayList<>();
      if (p.currentToken() != JsonToken.START_ARRAY) {
        return result;
      }
      while (p.nextToken() != JsonToken.END_ARRAY) {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
          result.add(p.getText());
        } else if (p.currentToken() == JsonToken.START_ARRAY) {
          // Some abilities are nested arrays — flatten them.
          // e.g. Monkey King: ["monkey_king_untransform", "monkey_king_transfiguration"]
          // at index 7 instead of a plain string.
          while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
              result.add(p.getText());
            } else {
              p.skipChildren();
            }
          }
        } else {
          p.skipChildren();
        }
      }
      return result;
    }
  }
}
