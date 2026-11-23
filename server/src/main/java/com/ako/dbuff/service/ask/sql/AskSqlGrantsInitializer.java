package com.ako.dbuff.service.ask.sql;

import com.ako.dbuff.service.ask.sql.AskSqlSchemaSummary.Column;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Re-grants the ask role on every start, as the table owner, so columns added later are covered. A
 * failure leaves the tool reporting that SQL is disabled; it never stops startup.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class AskSqlGrantsInitializer implements ApplicationRunner {

  private static final String COLUMNS =
      "select c.table_name, c.column_name, c.data_type from information_schema.columns c"
          + " join information_schema.tables t on t.table_schema = c.table_schema"
          + " and t.table_name = c.table_name"
          + " where c.table_schema = 'public' and t.table_type = 'BASE TABLE'"
          + " order by c.table_name, c.ordinal_position";

  private final DataSource primary;
  private final AskSqlProperties properties;
  private final AskSqlSchemaSummary summary;

  public AskSqlGrantsInitializer(
      DataSource primary, AskSqlProperties properties, AskSqlSchemaSummary summary) {
    this.primary = primary;
    this.properties = properties;
    this.summary = summary;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      Map<String, List<Column>> columns = readColumns();
      List<String> statements =
          AskSqlGrants.statements(
              properties.getUsername(),
              new ArrayList<>(columns.keySet()),
              properties.getDenyTables());
      try (Connection connection = primary.getConnection();
          Statement statement = connection.createStatement()) {
        for (String sql : statements) {
          statement.execute(sql);
        }
      }
      summary.store(columns, properties.getDenyTables());
      log.info(
          "Ask sql_query ready: {} tables granted to {}", columns.size(), properties.getUsername());
    } catch (Exception e) {
      log.warn("Ask sql_query disabled, grants failed: {}", e.getMessage());
    }
  }

  private Map<String, List<Column>> readColumns() throws SQLException {
    Map<String, List<Column>> columns = new LinkedHashMap<>();
    try (Connection connection = primary.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(COLUMNS)) {
      while (rs.next()) {
        columns
            .computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
            .add(new Column(rs.getString(2), rs.getString(3)));
      }
    }
    return columns;
  }
}
