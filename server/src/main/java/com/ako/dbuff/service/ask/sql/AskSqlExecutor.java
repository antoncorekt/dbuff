package com.ako.dbuff.service.ask.sql;

import com.ako.dbuff.service.ask.tool.AskToolException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs a guarded query on the read-only pool in a read-only transaction with a timeout. */
@Component
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class AskSqlExecutor {

  private final DataSource dataSource;
  private final AskSqlGuard guard;
  private final AskSqlProperties properties;

  public AskSqlExecutor(
      @Qualifier("askSqlDataSource") DataSource dataSource,
      AskSqlGuard guard,
      AskSqlProperties properties) {
    this.dataSource = dataSource;
    this.guard = guard;
    this.properties = properties;
  }

  public Map<String, Object> execute(String sql) {
    String wrapped = guard.check(sql);
    try (Connection connection = dataSource.getConnection()) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL statement_timeout = " + properties.getStatementTimeoutMs());
        try (ResultSet rs = statement.executeQuery(wrapped)) {
          return read(rs);
        }
      } finally {
        connection.rollback();
      }
    } catch (SQLException e) {
      throw new AskToolException("Query failed: " + e.getMessage());
    }
  }

  private Map<String, Object> read(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int count = meta.getColumnCount();
    List<String> columns = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      columns.add(meta.getColumnLabel(i));
    }
    List<List<Object>> rows = new ArrayList<>();
    while (rs.next()) {
      List<Object> row = new ArrayList<>(count);
      for (int i = 1; i <= count; i++) {
        row.add(json(rs.getObject(i)));
      }
      rows.add(row);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("columns", columns);
    result.put("rows", rows);
    result.put("truncated", rows.size() >= properties.getRowLimit());
    return result;
  }

  private static Object json(Object value) {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value instanceof BigDecimal d ? d.toPlainString() : value;
    }
    return value.toString();
  }
}
