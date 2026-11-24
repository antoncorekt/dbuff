package com.ako.dbuff.service.ask.sql;

import com.ako.dbuff.service.ask.sql.AskSqlSchemaSummary.Column;
import com.ako.dbuff.service.ask.tool.AskToolException;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("pgsql")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
class AskSqlPgTest {

  private static final String ASK_PASSWORD = "ask-secret";
  private static final String URL = System.getenv("SMOKE_PG_URL");

  private static AskSqlProperties properties;
  private static HikariDataSource askSource;
  private static AskSqlExecutor executor;

  @BeforeAll
  static void setUp() throws Exception {
    try (Connection admin = DriverManager.getConnection(URL, "postgres", "password");
        Statement st = admin.createStatement()) {
      st.execute("drop table if exists match_domain, player_domain, dbuf_instance_config");
      st.execute("create table match_domain(match_id bigint primary key, duration int)");
      st.execute("insert into match_domain values (1, 2400)");
      st.execute(
          "create table player_domain(id bigint primary key, name text, discord_user_id text)");
      st.execute("insert into player_domain values (7, 'Bob', 'discord-7')");
      st.execute("create table dbuf_instance_config(id bigint primary key, secret text)");

      String script =
          Files.readString(Path.of("..", "infrastructure", "sql", "ask-role.sql"))
              .replace(":'ask_password'", "'" + ASK_PASSWORD + "'");
      st.execute(script);

      properties = new AskSqlProperties();
      properties.setStatementTimeoutMs(500);
      for (String sql :
          AskSqlGrants.statements(
              "dbuff_ask",
              List.of("match_domain", "player_domain", "dbuf_instance_config"),
              properties.getDenyTables())) {
        st.execute(sql);
      }
    }
    askSource = new HikariDataSource();
    askSource.setJdbcUrl(URL);
    askSource.setUsername("dbuff_ask");
    askSource.setPassword(ASK_PASSWORD);
    askSource.setMaximumPoolSize(2);
    executor = new AskSqlExecutor(askSource, new AskSqlGuard(properties), properties);
  }

  @AfterAll
  static void tearDown() {
    if (askSource != null) {
      askSource.close();
    }
  }

  @Test
  void selectFromMatchTableWorks() {
    Map<String, Object> result = executor.execute("select match_id, duration from match_domain");

    assertThat(result.get("columns")).isEqualTo(List.of("match_id", "duration"));
    assertThat(result.get("rows")).isEqualTo(List.of(List.of(1L, 2400)));
    assertThat(result.get("truncated")).isEqualTo(false);
  }

  @Test
  void allowedPlayerColumnsAreReadable() {
    assertThat(executor.execute("select id, name from player_domain").get("rows"))
        .isEqualTo(List.of(List.of(7L, "Bob")));
  }

  @Test
  void discordUserIdIsDenied() {
    assertThatThrownBy(() -> executor.execute("select discord_user_id from player_domain"))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("permission denied");
  }

  @Test
  void deniedTableIsDenied() {
    assertThatThrownBy(() -> executor.execute("select * from dbuf_instance_config"))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("permission denied");
  }

  @Test
  void deleteFailsEvenOutsideTheGuard() throws Exception {
    try (Connection connection = askSource.getConnection();
        Statement st = connection.createStatement()) {
      assertThatThrownBy(() -> st.execute("delete from match_domain"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void sleepHitsTheStatementTimeout() {
    assertThatThrownBy(() -> executor.execute("select pg_sleep(10)"))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("statement timeout");
  }

  @Test
  void schemaSummaryHidesDiscordIds() {
    AskSqlSchemaSummary summary = new AskSqlSchemaSummary();
    summary.store(
        Map.of(
            "player_domain",
            List.of(new Column("id", "bigint"), new Column("discord_user_id", "text")),
            "dbuf_instance_config",
            List.of(new Column("secret", "text"))),
        properties.getDenyTables());

    assertThat(summary.text()).isEqualTo("player_domain(id bigint)\n");
  }
}
