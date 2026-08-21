package com.ako.dbuff.service.ranking;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsPeriodTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

  @Test
  void last7Days_startsSevenDaysBeforeToday() {
    StatsPeriod.Range range = StatsPeriod.LAST_7_DAYS.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(range.endDate()).isEqualTo(TODAY);
  }

  @Test
  void last30Days_startsThirtyDaysBeforeToday() {
    StatsPeriod.Range range = StatsPeriod.LAST_30_DAYS.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(range.endDate()).isEqualTo(TODAY);
  }

  @Test
  void allTime_hasNullStartDate() {
    StatsPeriod.Range range = StatsPeriod.ALL_TIME.resolve(TODAY, null);

    assertThat(range.startDate()).isNull();
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isFalse();
  }

  /** Calendar months, not 30-day multiples: "3 months ago" means the same day three months back. */
  @Test
  void monthlyPresets_countBackInCalendarMonths() {
    assertThat(StatsPeriod.LAST_3_MONTHS.resolve(TODAY, null).startDate())
        .isEqualTo(LocalDate.of(2026, 5, 19));
    assertThat(StatsPeriod.LAST_6_MONTHS.resolve(TODAY, null).startDate())
        .isEqualTo(LocalDate.of(2026, 2, 19));
    assertThat(StatsPeriod.LAST_12_MONTHS.resolve(TODAY, null).startDate())
        .isEqualTo(LocalDate.of(2025, 8, 19));
  }

  @Test
  void monthlyPresets_endToday_andNeverReportAFallback() {
    for (StatsPeriod period :
        List.of(StatsPeriod.LAST_3_MONTHS, StatsPeriod.LAST_6_MONTHS, StatsPeriod.LAST_12_MONTHS)) {
      StatsPeriod.Range range = period.resolve(TODAY, null);

      assertThat(range.endDate()).as("end of %s", period).isEqualTo(TODAY);
      assertThat(range.fellBack()).as("fallback flag of %s", period).isFalse();
    }
  }

  /** A month subtraction that lands on a shorter month must clamp, not roll over. */
  @Test
  void monthlyPresets_clampWhenTheTargetMonthIsShorter() {
    assertThat(StatsPeriod.LAST_3_MONTHS.resolve(LocalDate.of(2026, 5, 31), null).startDate())
        .isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void monthlyPresets_areOfferedAsChoices() {
    assertThat(StatsPeriod.fromChoiceValue("last_3_months")).isEqualTo(StatsPeriod.LAST_3_MONTHS);
    assertThat(StatsPeriod.fromChoiceValue("last_6_months")).isEqualTo(StatsPeriod.LAST_6_MONTHS);
    assertThat(StatsPeriod.fromChoiceValue("last_12_months")).isEqualTo(StatsPeriod.LAST_12_MONTHS);
  }

  @Test
  void currentPatch_usesSuppliedPatchStartDate() {
    StatsPeriod.Range range = StatsPeriod.CURRENT_PATCH.resolve(TODAY, LocalDate.of(2026, 8, 1));

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isFalse();
  }

  @Test
  void currentPatch_withoutPatchDate_fallsBackTo30DaysAndFlagsIt() {
    StatsPeriod.Range range = StatsPeriod.CURRENT_PATCH.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isTrue();
  }

  @Test
  void fromChoiceValue_isCaseInsensitive() {
    assertThat(StatsPeriod.fromChoiceValue("last_7_days")).isEqualTo(StatsPeriod.LAST_7_DAYS);
    assertThat(StatsPeriod.fromChoiceValue("LAST_7_DAYS")).isEqualTo(StatsPeriod.LAST_7_DAYS);
  }

  @Test
  void fromChoiceValue_unknownOrNull_defaultsTo30Days() {
    assertThat(StatsPeriod.fromChoiceValue(null)).isEqualTo(StatsPeriod.LAST_30_DAYS);
    assertThat(StatsPeriod.fromChoiceValue("nonsense")).isEqualTo(StatsPeriod.LAST_30_DAYS);
  }
}
