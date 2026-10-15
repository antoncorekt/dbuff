package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
          Map.entry("smoke", FactKind.SMOKE));

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
    Optional<SectionView> view;
    try {
      view = icons.withoutIcons(() -> section.view(input));
    } catch (RuntimeException e) {
      log.warn(
          "Section {} of match {} left out of the AI facts: {}",
          section.name(),
          input.matchId(),
          e.getMessage());
      return List.of();
    }
    List<String> texts = new ArrayList<>();
    view.ifPresent(
        v -> {
          texts.add(v.verdict());
          texts.addAll(v.bullets());
        });
    List<Fact> facts = new ArrayList<>();
    for (String text : texts) {
      String plain = plain(text);
      if (!plain.isEmpty()) {
        facts.add(new Fact(kind.id(facts.size() + 1), kind, plain));
      }
    }
    return facts;
  }

  private static String plain(String text) {
    return text == null
        ? ""
        : text.replace("**", "").replace("`", "").replaceAll("\\s*\\R\\s*", " ").strip();
  }
}
