package com.ako.dbuff.service.ask;

/**
 * The final answer of one question.
 *
 * @param text what to post
 * @param stopReason the model's stop reason of the last call
 * @param toolCalls how many tools were executed
 */
public record AskAnswer(String text, String stopReason, int toolCalls) {}
