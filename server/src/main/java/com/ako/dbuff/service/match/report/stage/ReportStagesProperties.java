package com.ako.dbuff.service.match.report.stage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Bound from {@code discord.report.stages.*}. Off: match threads behave as they always did. */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "discord.report.stages")
public class ReportStagesProperties {

  /** Staged header (Components V2, edited as the match progresses) and the basic player card. */
  private boolean enabled = false;
}
