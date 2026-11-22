package com.ako.dbuff.service.ask;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;

/** One call to the model, behind an interface so the loop can be tested without the network. */
public interface AskModel {

  BetaMessage create(MessageCreateParams params);

  boolean available();
}
