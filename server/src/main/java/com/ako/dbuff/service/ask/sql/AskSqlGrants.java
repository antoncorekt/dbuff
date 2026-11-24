package com.ako.dbuff.service.ask.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the GRANT and REVOKE statements that give the ask role its read access. */
public final class AskSqlGrants {

  static final String PLAYER_TABLE = "player_domain";
  static final List<String> PLAYER_COLUMNS = List.of("name", "id");

  private AskSqlGrants() {}

  /** True when {@code table} matches a deny entry; an entry ending in {@code *} is a prefix. */
  public static boolean isDenied(String table, List<String> denyList) {
    String name = table.toLowerCase(Locale.ROOT);
    for (String entry : denyList) {
      String pattern = entry.trim().toLowerCase(Locale.ROOT);
      if (pattern.endsWith("*")
          ? name.startsWith(pattern.substring(0, pattern.length() - 1))
          : name.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  public static List<String> statements(String role, List<String> tables, List<String> denyList) {
    String who = quote(role);
    List<String> sql = new ArrayList<>();
    sql.add("GRANT USAGE ON SCHEMA public TO " + who);
    for (String table : tables) {
      String target = "TABLE public." + quote(table);
      if (isDenied(table, denyList)) {
        sql.add("REVOKE ALL ON " + target + " FROM " + who);
      } else if (PLAYER_TABLE.equals(table)) {
        sql.add("REVOKE SELECT ON " + target + " FROM " + who);
        sql.add(
            "GRANT SELECT ("
                + String.join(", ", PLAYER_COLUMNS.stream().map(AskSqlGrants::quote).toList())
                + ") ON "
                + target
                + " TO "
                + who);
      } else {
        sql.add("GRANT SELECT ON " + target + " TO " + who);
      }
    }
    return sql;
  }

  static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
