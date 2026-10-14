package com.ako.dbuff.service.ai.reasons;

/** A language model that can be asked for the reasons of a match; the provider is behind it. */
public interface ReasonsModel {

  /** Whether the model can be called at all, for a provider whether its key is configured. */
  boolean available();

  /** The model a call goes to unless another one is asked for. */
  String defaultModel();

  /** One call to the named model; throws {@link ReasonsModelException} without an answer. */
  ModelAnswer ask(ReasonsPrompt prompt, String model);
}
