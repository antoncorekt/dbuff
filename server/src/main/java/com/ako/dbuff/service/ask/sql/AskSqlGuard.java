package com.ako.dbuff.service.ask.sql;

import com.ako.dbuff.service.ask.tool.AskToolException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Accepts exactly one SELECT/WITH statement and wraps it with a row limit. */
@Component
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class AskSqlGuard {

  private static final Pattern FORBIDDEN =
      Pattern.compile(
          "\\b(insert|update|delete|merge|drop|alter|create|grant|revoke|truncate|copy|call|do"
              + "|set|reset|lock|vacuum|listen|notify|set_config)\\b");

  /**
   * Dollar quotes and E'' strings end at places this scanner cannot follow, so they are refused.
   */
  private static final Pattern UNSCANNABLE_QUOTING = Pattern.compile("\\$\\w*\\$|\\b[eE]'");

  private static final Pattern FIRST_WORD = Pattern.compile("^(select|with)\\b");

  private final int rowLimit;

  public AskSqlGuard(AskSqlProperties properties) {
    this.rowLimit = properties.getRowLimit();
  }

  public String check(String sql) {
    if (sql == null) {
      throw new AskToolException("sql is required");
    }
    if (UNSCANNABLE_QUOTING.matcher(sql).find()) {
      throw new AskToolException("Dollar-quoted and E'' strings are not allowed");
    }
    String stripped = stripComments(sql).strip();
    if (stripped.endsWith(";")) {
      stripped = stripped.substring(0, stripped.length() - 1).strip();
    }
    if (stripped.isEmpty()) {
      throw new AskToolException("The statement is empty");
    }
    String code = maskQuoted(stripped).toLowerCase(Locale.ROOT);
    if (code.indexOf(';') >= 0) {
      throw new AskToolException("Only one statement is allowed");
    }
    if (!FIRST_WORD.matcher(code).find()) {
      throw new AskToolException("Only SELECT or WITH statements are allowed");
    }
    var forbidden = FORBIDDEN.matcher(code);
    if (forbidden.find()) {
      throw new AskToolException("Not allowed in a read-only query: " + forbidden.group(1));
    }
    return "select * from (" + stripped + ") q limit " + rowLimit;
  }

  /** Removes {@code --} and {@code /* *}{@code /} comments that are outside quotes. */
  static String stripComments(String sql) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"') {
        int end = quoteEnd(sql, i);
        out.append(sql, i, end);
        i = end;
      } else if (sql.startsWith("--", i)) {
        int nl = sql.indexOf('\n', i);
        i = nl < 0 ? sql.length() : nl;
      } else if (sql.startsWith("/*", i)) {
        int close = sql.indexOf("*/", i + 2);
        i = close < 0 ? sql.length() : close + 2;
        out.append(' ');
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  /** Replaces quoted strings and identifiers by spaces so their content is not read as SQL. */
  static String maskQuoted(String sql) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"') {
        int end = quoteEnd(sql, i);
        out.append(" ".repeat(end - i));
        i = end;
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  private static int quoteEnd(String sql, int start) {
    char quote = sql.charAt(start);
    int i = start + 1;
    while (i < sql.length()) {
      if (sql.charAt(i) == quote) {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return sql.length();
  }
}
