package com.ako.dbuff.service.match.backfill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code match-detail-backfill.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "match-detail-backfill")
public class MatchDetailBackfillProperties {
  private boolean enabled = true;
  /** Matches refetched per run; each costs one Dota API request from the shared rate limit. */
  private int batchSize = 20;
  private String cron = "0 7/10 * * * ?";
  /** Wait before retrying a match that was tried but not stored. */
  private int retryAfterHours = 24;
  /** Matches older than this are not retried after a failed attempt. */
  private int retryWithinDays = 7;
}
