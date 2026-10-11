package com.ako.dbuff.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Access control for the REST API, bound from {@code app.api.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.api")
public class ApiSecurityProperties {

  /** The key callers send in {@code X-Api-Key}; blank means none is configured. */
  @ToString.Exclude private String key;

  /**
   * Whether the API must be protected. When true and no key is set, every request is refused: a
   * missing key closes the API instead of leaving it open. Local development turns this off.
   */
  private boolean required = true;

  public boolean hasKey() {
    return key != null && !key.isBlank();
  }
}
