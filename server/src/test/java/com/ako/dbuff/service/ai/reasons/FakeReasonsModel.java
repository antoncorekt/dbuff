package com.ako.dbuff.service.ai.reasons;

import java.util.ArrayList;
import java.util.List;

/** A model that answers with a set text, or fails, and remembers what it was asked. */
class FakeReasonsModel implements ReasonsModel {
  final List<ReasonsPrompt> asked = new ArrayList<>();
  boolean available = true;
  String answer = "[]";
  RuntimeException failure;

  @Override
  public boolean available() {
    return available;
  }

  @Override
  public String modelName() {
    return "fake-model";
  }

  @Override
  public ModelAnswer ask(ReasonsPrompt prompt) {
    asked.add(prompt);
    if (failure != null) {
      throw failure;
    }
    return new ModelAnswer(answer, 321, modelName());
  }
}
