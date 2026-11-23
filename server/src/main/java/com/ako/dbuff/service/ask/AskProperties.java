package com.ako.dbuff.service.ask;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings of the Discord ask feature ({@code ask.*}). Off unless {@code ask.enabled=true}. */
@Data
@Component
@ConfigurationProperties(prefix = "ask")
public class AskProperties {

  private boolean enabled = false;
  private String apiKey = "";
  private String model = "claude-sonnet-5-5";
  private String effort = "medium";
  private int maxTokens = 16000;
  private int timeoutSeconds = 120;
  private int maxToolCallsPerAnswer = 15;
  private int maxQuestionsPerChannelPerDay = 50;
  private int maxTurnsPerThread = 30;
  private int toolResultMaxChars = 20000;
}
