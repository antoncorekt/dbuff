package com.ako.dbuff.service.ai.reasons;

/** What the model is asked: the standing instructions and the facts of one match. */
public record ReasonsPrompt(String system, String user) {}
