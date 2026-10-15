package com.ako.dbuff.service.replay.report.chart;

import java.util.Locale;

/** How chart axes write their numbers. */
public final class ChartFormat {
  private ChartFormat() {}

  /** Game seconds as {@code m:ss}; the minute may pass 59. */
  public static String clock(double seconds) {
    long whole = Math.round(seconds);
    String sign = whole < 0 ? "-" : "";
    long abs = Math.abs(whole);
    return String.format(Locale.ROOT, "%s%d:%02d", sign, abs / 60, abs % 60);
  }

  /** A number as {@code 850}, {@code 1.2k}, {@code -3k}. */
  public static String compact(double value) {
    double abs = Math.abs(value);
    if (abs < 1000) {
      return String.valueOf(Math.round(value));
    }
    String thousands = String.format(Locale.ROOT, "%.1f", value / 1000);
    return (thousands.endsWith(".0") ? thousands.substring(0, thousands.length() - 2) : thousands)
        + "k";
  }

  static String value(ChartSpec.ValueFormat format, double value) {
    return switch (format) {
      case COMPACT -> compact(value);
    };
  }
}
