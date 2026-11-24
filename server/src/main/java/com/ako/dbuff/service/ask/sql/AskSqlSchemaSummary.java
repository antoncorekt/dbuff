package com.ako.dbuff.service.ask.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The schema text for the tool description, built once at startup and then kept. */
@Component
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class AskSqlSchemaSummary {

  /** One column as {@code name type}. */
  public record Column(String name, String type) {}

  private volatile String summary;

  public boolean isReady() {
    return summary != null;
  }

  public String text() {
    return summary == null ? "" : summary;
  }

  /** Keeps the rendered summary of the allowed tables; this is what marks the tool ready. */
  public void store(Map<String, List<Column>> columnsByTable, List<String> denyList) {
    this.summary = render(columnsByTable, denyList);
  }

  static String render(Map<String, List<Column>> columnsByTable, List<String> denyList) {
    StringBuilder out = new StringBuilder();
    new TreeMap<>(columnsByTable)
        .forEach(
            (table, columns) -> {
              if (AskSqlGrants.isDenied(table, denyList)) {
                return;
              }
              List<Column> visible = new ArrayList<>(columns);
              if (AskSqlGrants.PLAYER_TABLE.equals(table)) {
                visible.removeIf(c -> !AskSqlGrants.PLAYER_COLUMNS.contains(c.name()));
              }
              TreeSet<String> parts = new TreeSet<>();
              visible.forEach(c -> parts.add(c.name() + " " + c.type()));
              out.append(table).append('(').append(String.join(", ", parts)).append(")\n");
            });
    return out.toString();
  }
}
