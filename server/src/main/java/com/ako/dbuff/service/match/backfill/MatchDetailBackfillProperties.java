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
}
