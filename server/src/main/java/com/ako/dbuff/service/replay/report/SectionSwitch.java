package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.InstanceSettings;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides per section whether it posts. Automatic posting: the channel's own list, else the global
 * {@code replay.report.default-sections} when that is set (even empty), else, for a deployment that
 * set neither, {@code replay.report.sections.<name>.enabled} when set and the section's own default
 * otherwise. {@code enabled=false} is a kill switch that hides the section everywhere, on demand
 * too. Names match ignoring case, '-' and '_', so the YAML and the environment-variable forms of a
 * property find the same section. While the report is off, start-up does not check the properties;
 * when it is on, it fails on a duplicate section name or a property for a section that does not
 * exist.
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
    Set<String> unlisted =
        properties.getDefaultSections() == null
            ? Set.of()
            : properties.getDefaultSections().stream()
                .map(SectionSwitch::normalise)
                .filter(name -> !known.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
    if (!unlisted.isEmpty()) {
      throw new IllegalStateException(
          "replay.report.default-sections names unknown sections "
              + unlisted
              + "; known sections: "
              + knownNames);
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

  /** Whether the section posts automatically in a channel with these settings. */
  public boolean isAutomatic(ReplayReportSection section, InstanceSettings settings) {
    if (isHidden(section)) {
      return false;
    }
    Optional<List<String>> channel = settings == null ? Optional.empty() : settings.sectionNames();
    if (channel.isPresent()) {
      return listed(channel.get(), section);
    }
    if (properties.getDefaultSections() != null) {
      return listed(properties.getDefaultSections(), section);
    }
    return isEnabled(section);
  }

  /** Whether the kill switch hides the section: nobody can post it, on demand included. */
  public boolean isHidden(ReplayReportSection section) {
    ReplayReportProperties.SectionToggle toggle = toggles().get(normalise(section.name()));
    return toggle != null && Boolean.FALSE.equals(toggle.getEnabled());
  }

  /** The registered section a typed name stands for, ignoring case, '-' and '_'. */
  public Optional<ReplayReportSection> find(String name) {
    if (name == null) {
      return Optional.empty();
    }
    String wanted = normalise(name);
    return sections.stream().filter(s -> normalise(s.name()).equals(wanted)).findFirst();
  }

  /** Names of the sections that are not hidden, in posting order. */
  public List<String> visibleNames() {
    return sections.stream()
        .filter(s -> !isHidden(s))
        .sorted(Comparator.comparingInt(ReplayReportSection::order))
        .map(ReplayReportSection::name)
        .toList();
  }

  private static boolean listed(List<String> names, ReplayReportSection section) {
    String wanted = normalise(section.name());
    return names.stream().anyMatch(name -> normalise(name).equals(wanted));
  }

  /** The section's own switch: its property when set, else its default. */
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
