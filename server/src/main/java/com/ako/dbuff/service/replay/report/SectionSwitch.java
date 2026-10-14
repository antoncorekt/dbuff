package com.ako.dbuff.service.replay.report;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides per section whether it posts: {@code replay.report.sections.<name>.enabled} when set,
 * otherwise the section's own default. Names match ignoring case, '-' and '_', so the YAML and the
 * environment-variable forms of a property find the same section. While the report is off, start-up
 * does not check the properties; when it is on, it fails on a duplicate section name or a property
 * for a section that does not exist.
 */
@Component
@RequiredArgsConstructor
public class SectionSwitch {
  private final ReplayReportProperties properties;
  private final List<ReplayReportSection> sections;

  @PostConstruct
  void validate() {
    sections.forEach(SectionSwitch::requireName);
    if (!properties.isEnabled()) {
      return;
    }
    Set<String> known = new TreeSet<>();
    Set<String> knownNames = new TreeSet<>();
    Set<String> duplicates = new TreeSet<>();
    sections.forEach(
        s -> {
          knownNames.add(s.name());
          if (!known.add(normalise(s.name()))) {
            duplicates.add(s.name());
          }
        });
    if (!duplicates.isEmpty()) {
      throw new IllegalStateException("Replay report sections share a name: " + duplicates);
    }
    Set<String> unknown = new HashSet<>(toggles().keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "replay.report.sections names unknown sections "
              + new TreeSet<>(unknown)
              + "; known sections: "
              + knownNames);
    }
  }

  public boolean isEnabled(ReplayReportSection section) {
    ReplayReportProperties.SectionToggle toggle = toggles().get(normalise(section.name()));
    return toggle != null && toggle.getEnabled() != null
        ? toggle.getEnabled()
        : section.defaultEnabled();
  }

  private Map<String, ReplayReportProperties.SectionToggle> toggles() {
    Map<String, ReplayReportProperties.SectionToggle> bound = properties.getSections();
    Map<String, ReplayReportProperties.SectionToggle> byName = new HashMap<>();
    if (bound != null) {
      bound.forEach((name, toggle) -> byName.put(normalise(name), toggle));
    }
    return byName;
  }

  private static void requireName(ReplayReportSection section) {
    if (section.name() == null || section.name().isBlank()) {
      throw new IllegalStateException(
          "Replay report section " + section.getClass().getName() + " has no name");
    }
  }

  private static String normalise(String name) {
    return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
  }
}
