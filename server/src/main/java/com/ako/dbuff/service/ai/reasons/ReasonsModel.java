package com.ako.dbuff.service.ai.reasons;

/** A language model that can be asked for the reasons of a match; the provider is behind it. */
public interface ReasonsModel {

  /** Whether the model can be called at all, for a provider whether its key is configured. */
  boolean available();

  /** The name of the model a call goes to; part of what a stored answer was made with. */
  String modelName();

  /** One call; throws {@link ReasonsModelException} when it produced no answer. */
  ModelAnswer ask(ReasonsPrompt prompt);
}
