package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.ask.sql.AskSqlExecutor;
import com.ako.dbuff.service.ask.sql.AskSqlProperties;
import com.ako.dbuff.service.ask.sql.AskSqlSchemaSummary;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Free-form read-only SQL over the stored match data, for questions the curated tools miss. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class SqlQueryTool implements AskTool {

  private final AskSqlExecutor executor;
  private final AskSqlSchemaSummary summary;
  private final AskSqlProperties properties;

  @Override
  public String name() {
    return "sql_query";
  }

  @Override
  public String description() {
    return "Run one read-only PostgreSQL SELECT (or WITH ... SELECT) over the stored Dota data."
        + " Hero, ability, item and game mode ids come from lookup_constants; names are not"
        + " stored in match rows. At most "
        + properties.getRowLimit()
        + " rows come back; aggregate in SQL. Tables:\n"
        + summary.text();
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("sql", Map.of("type", "string", "description", "A single SELECT statement")),
        "required",
        List.of("sql"));
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    if (!summary.isReady()) {
      throw new AskToolException("sql disabled");
    }
    JsonNode sql = input == null ? null : input.get("sql");
    if (sql == null || sql.asText().isBlank()) {
      throw new AskToolException("sql is required");
    }
    return executor.execute(sql.asText());
  }
}
