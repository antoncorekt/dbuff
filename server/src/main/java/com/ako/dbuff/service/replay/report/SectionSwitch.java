package com.ako.dbuff.service.replay.report;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides per section whether it posts: {@code replay.report.sections.<name>.enabled} when set,
 * otherwise the section's own default. Start-up fails on a duplicate section name or a property for
 * a section that does not exist.
 */
@Component
@RequiredArgsConstructor
public class SectionSwitch {
  private final ReplayReportProperties properties;
  private final List<ReplayReportSection> sections;

  @PostConstruct
  void validate() {
    Set<String> known = new TreeSet<>();
    Set<String> duplicates = new TreeSet<>();
    sections.forEach(
        s -> {
          if (!known.add(s.name())) {
            duplicates.add(s.name());
          }
        });
    if (!duplicates.isEmpty()) {
      throw new IllegalStateException("Replay report sections share a name: " + duplicates);
    }
    Set<String> unknown = new HashSet<>(properties.getSections().keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "replay.report.sections names unknown sections "
              + new TreeSet<>(unknown)
              + "; known sections: "
              + known);
    }
  }

  public boolean isEnabled(ReplayReportSection section) {
    Map<String, ReplayReportProperties.SectionToggle> toggles = properties.getSections();
    ReplayReportProperties.SectionToggle toggle = toggles.get(section.name());
    return toggle != null && toggle.getEnabled() != null
        ? toggle.getEnabled()
        : section.defaultEnabled();
  }
}
