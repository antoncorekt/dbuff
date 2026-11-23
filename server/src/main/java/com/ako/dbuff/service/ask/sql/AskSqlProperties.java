package com.ako.dbuff.service.ask.sql;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings of the guarded {@code sql_query} tool ({@code ask.sql.*}). Off by default. */
@Data
@Component
@ConfigurationProperties(prefix = "ask.sql")
public class AskSqlProperties {

  private boolean enabled = false;
  private String url = "";
  private String username = "dbuff_ask";
  private String password = "";
  private int rowLimit = 200;
  private int statementTimeoutMs = 5000;
  private List<String> denyTables =
      new ArrayList<>(
          List.of(
              "dbuf_instance_*",
              "discord_icon",
              "match_report_thread_domain",
              "replay_report_posted_domain",
              "ask_*",
              "qrtz_*",
              "replay_combat_log_domain"));
}
