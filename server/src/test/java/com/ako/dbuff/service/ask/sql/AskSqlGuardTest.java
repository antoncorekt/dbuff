package com.ako.dbuff.service.ask.sql;

import com.ako.dbuff.service.ask.tool.AskToolException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskSqlGuardTest {

  private final AskSqlGuard guard = new AskSqlGuard(new AskSqlProperties());

  @Test
  void wrapsWithRowLimit() {
    assertThat(guard.check("select 1")).isEqualTo("select * from (select 1) q limit 200");
  }

  @Test
  void allowsOneTrailingSemicolonAndWith() {
    assertThat(guard.check("with a as (select 1) select * from a ; "))
        .isEqualTo("select * from (with a as (select 1) select * from a) q limit 200");
  }

  @Test
  void rejectsMultipleStatements() {
    assertThatThrownBy(() -> guard.check("select 1; select 2"))
        .isInstanceOf(AskToolException.class);
  }

  @Test
  void rejectsDml() {
    assertThatThrownBy(() -> guard.check("delete from match_domain"))
        .isInstanceOf(AskToolException.class);
  }

  @Test
  void rejectsDmlHiddenBehindComments() {
    assertThatThrownBy(() -> guard.check("/* select */ delete from match_domain"))
        .isInstanceOf(AskToolException.class);
    assertThatThrownBy(() -> guard.check("select 1 --\n; drop table match_domain"))
        .isInstanceOf(AskToolException.class);
  }

  @Test
  void rejectsWithDelete() {
    assertThatThrownBy(
            () -> guard.check("with d as (delete from match_domain returning id) select * from d"))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("delete");
  }

  @Test
  void rejectsEmpty() {
    assertThatThrownBy(() -> guard.check(" -- nothing\n ; ")).isInstanceOf(AskToolException.class);
  }

  @Test
  void keywordsInsideStringsAreFine() {
    assertThat(guard.check("select 'delete; drop' as x")).contains("'delete; drop'");
  }

  @Test
  void commentsAreStrippedFromTheWrappedSql() {
    assertThat(guard.check("select 1 -- hi\n")).isEqualTo("select * from (select 1) q limit 200");
  }
}
