package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.replay.report.Finding;
import com.ako.dbuff.service.replay.report.FindingKind;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.SectionFindings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The facts the replay report sections already say in plain words, one fact per verdict or bullet.
 * The sections are looked up lazily because the reasons section is one of them.
 */
@Slf4j
@Component
class SectionFacts {
  private static final Map<String, FactKind> KINDS =
      Map.ofEntries(
          Map.entry("important-fights", FactKind.FIGHT),
          Map.entry("lanes", FactKind.LANE),
          Map.entry("objectives", FactKind.OBJECTIVE),
          Map.entry("swing", FactKind.SWING),
          Map.entry("endgame", FactKind.ENDGAME),
          Map.entry("vision", FactKind.VISION),
          Map.entry("items", FactKind.ITEM),
          Map.entry("opening", FactKind.RUNES),
          Map.entry("outliers", FactKind.OUTLIER),
          Map.entry("rotations", FactKind.ROTATION),
          Map.entry("smoke", FactKind.SMOKE),
          Map.entry("mid-activity", FactKind.MID),
          Map.entry("skill-picks", FactKind.PICK));

  private static FactKind factKind(FindingKind kind) {
    return switch (kind) {
      case PICK -> FactKind.PICK;
      case BUILD -> FactKind.BUILD;
      case MISSED -> FactKind.MISSED;
    };
  }

  private final ObjectProvider<ReplayReportSection> sections;
  private final IconService icons;

  SectionFacts(ObjectProvider<ReplayReportSection> sections, IconService icons) {
    this.sections = sections;
    this.icons = icons;
  }

  List<Fact> build(ReplayReportInput input) {
    List<Fact> facts = new ArrayList<>();
    sections
        .orderedStream()
        .filter(s -> KINDS.containsKey(s.name()))
        .sorted(Comparator.comparingInt(ReplayReportSection::order))
        .forEach(s -> facts.addAll(factsOf(s, KINDS.get(s.name()), input)));
    return facts;
  }

  private List<Fact> factsOf(ReplayReportSection section, FactKind kind, ReplayReportInput input) {
    try {
      return icons.withoutIcons(() -> facts(section, kind, input));
    } catch (RuntimeException e) {
      log.warn(
          "Section {} of match {} left out of the AI facts: {}",
          section.name(),
          input.matchId(),
          e.getMessage());
      return List.of();
    }
  }

  private static List<Fact> facts(
      ReplayReportSection section, FactKind kind, ReplayReportInput input) {
    Map<FactKind, Integer> counts = new EnumMap<>(FactKind.class);
    List<Fact> facts = new ArrayList<>();
    for (Finding finding : findings(section, input)) {
      String plain = plain(finding.text());
      if (!plain.isEmpty()) {
        FactKind own = finding.kind() == null ? kind : factKind(finding.kind());
        int number = counts.merge(own, 1, Integer::sum);
        facts.add(new Fact(own.id(number), own, plain));
      }
    }
    return facts;
  }

  private static List<Finding> findings(ReplayReportSection section, ReplayReportInput input) {
    if (section instanceof SectionFindings kinded) {
      return kinded.findings(input);
    }
    List<Finding> found = new ArrayList<>();
    section
        .view(input)
        .ifPresent(
            v -> {
              found.add(new Finding(null, v.verdict()));
              v.bullets().forEach(b -> found.add(new Finding(null, b)));
            });
    return found;
  }

  private static String plain(String text) {
    return text == null
        ? ""
        : text.replace("**", "").replace("`", "").replaceAll("\\s*\\R\\s*", " ").strip();
  }
}
