package com.ako.dbuff.service.instance;

import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Validates and applies per-channel settings changes. Every rejection is an {@link
 * IllegalArgumentException} whose message is written for the person who typed the value, because
 * the Discord command shows it to them as is.
 */
@Component
public class InstanceSettingsApplier {

  /** Value that clears a setting back to what applies when nothing is chosen. */
  public static final String DEFAULT = "default";

  private static final String OFF = "off";
  private static final Pattern WINDOW =
      Pattern.compile("^\\s*(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})\\s*$");
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

  private static final String NONE = "none";
  private static final int MAX_LANGUAGE_LENGTH = 40;
  private static final Pattern LANGUAGE = Pattern.compile("^\\p{L}[\\p{L} \\-]*$");

  private final GameModeResolver gameModeResolver;
  private final SectionSwitch sectionSwitch;

  /** The switch is lazy: it holds every report section, which reach back into the services. */
  public InstanceSettingsApplier(
      GameModeResolver gameModeResolver, @Lazy SectionSwitch sectionSwitch) {
    this.gameModeResolver = gameModeResolver;
    this.sectionSwitch = sectionSwitch;
  }

  /** Applies whichever settings the request carries to {@code target}, leaving the rest alone. */
  public void apply(InstanceSettings target, UpdateInstanceRequest request) {
    if (request.getAiSummaryEnabled() != null) {
      target.setAiSummaryEnabled(request.getAiSummaryEnabled());
    }
    if (request.getReplayReportEnabled() != null) {
      target.setReplayReportEnabled(request.getReplayReportEnabled());
    }
    if (request.getReportDetail() != null) {
      target.setReportDetail(parseDetail(request.getReportDetail()));
    }
    if (request.getTimezone() != null) {
      target.setTimezone(parseTimezone(request.getTimezone()));
    }
    if (request.getQuietHours() != null) {
      applyQuietHours(target, request.getQuietHours());
    }
    if (request.getDefaultGameModes() != null) {
      target.setDefaultGameModes(parseModes(request.getDefaultGameModes()));
    }
    if (request.getSections() != null) {
      target.setSections(parseSections(request.getSections()));
    }
    if (request.getLanguage() != null) {
      target.setLanguage(parseLanguage(request.getLanguage()));
    }
  }

  /** Canonical comma-separated names, empty string for none, null to follow the server. */
  private String parseSections(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || DEFAULT.equalsIgnoreCase(trimmed)) {
      return null;
    }
    if (NONE.equalsIgnoreCase(trimmed)) {
      return "";
    }
    Set<String> names = new LinkedHashSet<>();
    for (String token : trimmed.split("[,\\s]+")) {
      if (token.isEmpty()) {
        continue;
      }
      ReplayReportSection section =
          sectionSwitch
              .find(token)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Unknown report section `"
                              + token
                              + "`. Valid sections: "
                              + String.join(", ", sectionSwitch.visibleNames())
                              + ". Use `none` for no automatic sections or `default` for the"
                              + " server's list."));
      if (sectionSwitch.isHidden(section)) {
        throw new IllegalArgumentException(
            "Report section `"
                + section.name()
                + "` is switched off on the server. Valid sections: "
                + String.join(", ", sectionSwitch.visibleNames())
                + ".");
      }
      names.add(section.name());
    }
    return String.join(",", names);
  }

  private String parseLanguage(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || DEFAULT.equalsIgnoreCase(trimmed)) {
      return null;
    }
    if (trimmed.length() > MAX_LANGUAGE_LENGTH || !LANGUAGE.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(
          "Could not read language `"
              + trimmed
              + "`. Use a language name such as English or Russian, or `default`.");
    }
    return trimmed;
  }

  /**
   * Makes the modes a channel was registered to track the modes {@code /stats} defaults to, so the
   * two agree. Unreadable modes are skipped: registration already validated them its own way, and
   * failing it here would reject a channel over a convenience.
   */
  public void seedDefaultModes(InstanceSettings target, Set<String> registeredModes) {
    if (registeredModes == null || registeredModes.isEmpty()) {
      return;
    }
    try {
      target.setDefaultGameModes(parseModes(registeredModes));
    } catch (IllegalArgumentException ignored) {
      // Leave the default as it was.
    }
  }

  private ReportDetail parseDetail(String raw) {
    try {
      return ReportDetail.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown detail level `" + raw + "`. Use minimal, standard or full.");
    }
  }

  private String parseTimezone(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || DEFAULT.equalsIgnoreCase(trimmed)) {
      return null;
    }
    try {
      return ZoneId.of(trimmed).getId();
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(
          "Unknown timezone `"
              + trimmed
              + "`. Use a name such as Europe/Kyiv or America/New_York.");
    }
  }

  private void applyQuietHours(InstanceSettings target, String raw) {
    String trimmed = raw.trim();
    if (DEFAULT.equalsIgnoreCase(trimmed)) {
      target.setQuietHoursEnabled(null);
      target.setQuietStart(null);
      target.setQuietEnd(null);
      return;
    }
    if (OFF.equalsIgnoreCase(trimmed)) {
      target.setQuietHoursEnabled(false);
      target.setQuietStart(null);
      target.setQuietEnd(null);
      return;
    }
    Matcher window = WINDOW.matcher(trimmed);
    if (!window.matches()) {
      throw new IllegalArgumentException(
          "Could not read quiet hours `"
              + trimmed
              + "`. Use a window like 01:00-09:00, or `off`, or `default`.");
    }
    LocalTime start = parseClock(window.group(1), trimmed);
    LocalTime end = parseClock(window.group(2), trimmed);
    if (start.equals(end)) {
      throw new IllegalArgumentException(
          "Quiet hours start and end are the same time. Use `off` to turn them off.");
    }
    target.setQuietHoursEnabled(true);
    target.setQuietStart(start);
    target.setQuietEnd(end);
  }

  private LocalTime parseClock(String clock, String original) {
    try {
      return LocalTime.parse(clock, CLOCK);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(
          "Could not read quiet hours `" + original + "`: `" + clock + "` is not a time of day.");
    }
  }

  /** Canonical comma-separated tokens, null to clear. */
  private String parseModes(Set<String> tokens) {
    Set<String> named =
        tokens.stream()
            .filter(token -> token != null && !token.isBlank())
            .map(String::trim)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (named.isEmpty()) {
      throw new IllegalArgumentException("Name at least one game mode, or `default` to clear it.");
    }
    if (named.stream().anyMatch(DEFAULT::equalsIgnoreCase)) {
      if (named.size() > 1) {
        throw new IllegalArgumentException(
            "`default` clears the choice and cannot be combined with other modes.");
      }
      return null;
    }
    GameModeSelection selection = gameModeResolver.resolve(named);
    if (selection.hasUnresolved()) {
      String unknown = String.join("`, `", selection.unresolvedNames());
      Optional<String> suggestion =
          gameModeResolver.suggest(selection.unresolvedNames().iterator().next());
      throw new IllegalArgumentException(
          "Unknown game mode `"
              + unknown
              + "`."
              + suggestion.map(name -> " Did you mean `" + name + "`?").orElse(""));
    }
    return selection.isAllModes()
        ? GameModeResolver.ALL
        : String.join(",", selection.canonicalNames());
  }
}
