package com.ako.dbuff.service.replay.smoke;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables a test class before its Spring context (and with it Hibernate's schema update) starts
 * unless the database named by {@code SMOKE_PG_URL} runs on this machine.
 */
public class LocalDatabase implements ExecutionCondition {
  static final String DEFAULT_URL = "jdbc:postgresql://localhost:5544/dbuff";

  private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    String url = System.getenv("SMOKE_PG_URL");
    url = url == null || url.isBlank() ? DEFAULT_URL : url;
    return isLocal(url)
        ? ConditionEvaluationResult.enabled("The database is local")
        : ConditionEvaluationResult.disabled(
            "The test changes the schema and commits data, so SMOKE_PG_URL must name localhost");
  }

  /** True only when the host of the URL itself, not a user, path or parameter, is this machine. */
  static boolean isLocal(String jdbcUrl) {
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
      return false;
    }
    try {
      String host = URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
      return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
