package com.ako.dbuff.service.ask.sql;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskSqlGrantsTest {

  private final List<String> deny = new AskSqlProperties().getDenyTables();

  @Test
  void grantsAllowedAndRevokesDenied() {
    List<String> sql =
        AskSqlGrants.statements(
            "dbuff_ask",
            List.of("match_domain", "dbuf_instance_config", "ask_thread_domain", "qrtz_triggers"),
            deny);

    assertThat(sql)
        .containsExactly(
            "GRANT USAGE ON SCHEMA public TO \"dbuff_ask\"",
            "GRANT SELECT ON TABLE public.\"match_domain\" TO \"dbuff_ask\"",
            "REVOKE ALL ON TABLE public.\"dbuf_instance_config\" FROM \"dbuff_ask\"",
            "REVOKE ALL ON TABLE public.\"ask_thread_domain\" FROM \"dbuff_ask\"",
            "REVOKE ALL ON TABLE public.\"qrtz_triggers\" FROM \"dbuff_ask\"");
  }

  @Test
  void playerTableGetsColumnGrantOnly() {
    List<String> sql = AskSqlGrants.statements("dbuff_ask", List.of("player_domain"), deny);

    assertThat(sql)
        .containsExactly(
            "GRANT USAGE ON SCHEMA public TO \"dbuff_ask\"",
            "REVOKE SELECT ON TABLE public.\"player_domain\" FROM \"dbuff_ask\"",
            "GRANT SELECT (\"name\", \"id\") ON TABLE public.\"player_domain\" TO \"dbuff_ask\"");
  }

  @Test
  void denyMatchingIsPrefixForStarAndExactOtherwise() {
    assertThat(AskSqlGrants.isDenied("discord_icon", deny)).isTrue();
    assertThat(AskSqlGrants.isDenied("discord_icon_extra", deny)).isFalse();
    assertThat(AskSqlGrants.isDenied("dbuf_instance_x", deny)).isTrue();
    assertThat(AskSqlGrants.isDenied("match_domain", deny)).isFalse();
  }
}
