package com.ako.dbuff.service.ask;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

/** Turns messages into the JSON that is stored, and back, without losing a block. */
@Component
public class AskHistoryCodec {

  public String toJson(BetaMessage message) {
    return toJson(message.toParam());
  }

  public String toJson(BetaMessageParam param) {
    try {
      return ObjectMappers.jsonMapper().writeValueAsString(param);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize ask turn", e);
    }
  }

  public BetaMessageParam fromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, BetaMessageParam.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read stored ask turn", e);
    }
  }
}
