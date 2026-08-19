package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.PatchConstant;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the start date of the current Dota patch, for {@code StatsPeriod.CURRENT_PATCH}.
 *
 * <p>Returns empty rather than guessing whenever the constant is missing or its date is not in a
 * shape we recognise. Callers degrade to a fixed window and <em>say so</em>; presenting a 30-day
 * window as "current patch" would silently answer a different question.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentPatchDateResolver {

  /** OpenDota dates look like {@code 2024-08-21T20:22:35.000Z}; some are bare dates. */
  private static final int ISO_DATE_LENGTH = 10;

  private final ConstantsManagers constantsManagers;

  /**
   * The start date of the highest-numbered known patch.
   *
   * @return the patch start date, or empty when it cannot be determined
   */
  public Optional<LocalDate> getCurrentPatchStartDate() {
    try {
      Map<String, PatchConstant> patches = constantsManagers.getPatchConstantMap();
      if (patches == null || patches.isEmpty()) {
        return Optional.empty();
      }
      return patches.values().stream()
          .filter(patch -> patch != null && patch.getId() != null)
          .max(Comparator.comparingLong(PatchConstant::getId))
          .flatMap(patch -> parseDate(patch.getDate()));
    } catch (RuntimeException e) {
      // Constants come from a cached remote API; an outage must not break /stats.
      log.warn("Could not determine current patch start date", e);
      return Optional.empty();
    }
  }

  private Optional<LocalDate> parseDate(String raw) {
    if (raw == null || raw.length() < ISO_DATE_LENGTH) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate());
    } catch (DateTimeParseException notAnInstant) {
      try {
        return Optional.of(LocalDate.parse(raw.substring(0, ISO_DATE_LENGTH)));
      } catch (DateTimeParseException notADate) {
        log.warn("Unrecognised patch date format: {}", raw);
        return Optional.empty();
      }
    }
  }
}
