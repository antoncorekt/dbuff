package com.ako.dbuff.service.ai.reasons;

/**
 * The raw text a model answered with and the tokens the call used, null when it did not say.
 *
 * @param model the model that answered
 */
public record ModelAnswer(String text, Integer tokensUsed, String model) {}
