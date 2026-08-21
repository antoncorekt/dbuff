package com.ako.dbuff.service.ranking;

import java.time.LocalDate;

/**
 * Preset time ranges for statistics queries.
 *
 * <p>{@code resolve} takes today's date and the current patch's start date as parameters rather
 * than reading a clock or a service, so the enum stays trivially testable. Callers supply them.
 */
public enum StatsPeriod {
  LAST_7_DAYS("Last 7 days"),
  LAST_30_DAYS("Last 30 days"),
  LAST_3_MONTHS("Last 3 months"),
  LAST_6_MONTHS("Last 6 months"),
  LAST_12_MONTHS("Last 12 months"),
  CURRENT_PATCH("Current patch"),
  ALL_TIME("All time");

  /** Fallback when a caller supplies no period, or an unrecognised one. */
  public static final StatsPeriod DEFAULT = LAST_30_DAYS;

  private static final int DEFAULT_DAYS = 30;

  private final String displayName;

  StatsPeriod(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  /** The value submitted by a Discord choice option, e.g. {@code last_7_days}. */
  public String getChoiceValue() {
    return name().toLowerCase();
  }

  /**
   * A resolved date range. {@code startDate} is null for {@link #ALL_TIME}, which the repositories
   * interpret as "no lower bound". {@code fellBack} is true when {@link #CURRENT_PATCH} could not
   * determine a patch start date and silently degraded to 30 days — callers should say so rather
   * than presenting the result as patch-scoped.
   */
  public record Range(LocalDate startDate, LocalDate endDate, boolean fellBack) {}

  /**
   * Resolves this period into a concrete date range.
   *
   * @param today the end of the range
   * @param patchStartDate start date of the current patch, or null if unknown
   * @return the resolved range
   */
  public Range resolve(LocalDate today, LocalDate patchStartDate) {
    return switch (this) {
      case LAST_7_DAYS -> new Range(today.minusDays(7), today, false);
      case LAST_30_DAYS -> new Range(today.minusDays(DEFAULT_DAYS), today, false);
      case LAST_3_MONTHS -> new Range(today.minusMonths(3), today, false);
      case LAST_6_MONTHS -> new Range(today.minusMonths(6), today, false);
      case LAST_12_MONTHS -> new Range(today.minusMonths(12), today, false);
      case ALL_TIME -> new Range(null, today, false);
      case CURRENT_PATCH ->
          patchStartDate != null
              ? new Range(patchStartDate, today, false)
              : new Range(today.minusDays(DEFAULT_DAYS), today, true);
    };
  }

  /** Parses a Discord choice value, defaulting to {@link #DEFAULT} for null or unknown input. */
  public static StatsPeriod fromChoiceValue(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    for (StatsPeriod period : values()) {
      if (period.name().equalsIgnoreCase(value)) {
        return period;
      }
    }
    return DEFAULT;
  }
}
