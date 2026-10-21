package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.analysis.skill.SkillCatalogEntry;
import com.ako.dbuff.service.replay.analysis.skill.SkillCatalogFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One line per focus hero and catalogued skill about how his casts went (hooks that hit, shards
 * that held, promises that saved), from the stored {@code skill_effect} rows only, at most {@code
 * maxFindingsPerSection} lines in all. Off unless its property turns it on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillEffectsSection implements ReplayReportSection {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final SkillEffectRepo repo;
  private final SkillCatalogFile catalog;
  private final ReportHeroNames heroNames;
  private final AbilityLabels labels;

  @Override
  public String name() {
    return "skill-effects";
  }

  @Override
  public int order() {
    return 14;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<SkillEffectDomain> rows = repo.findByMatchId(input.matchId());
    if (input.focusSlots() == null) {
      return List.of();
    }
    List<SkillCatalogEntry> entries = catalog.entries();
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    List<String> lines = new ArrayList<>();
    for (Long slot : new TreeSet<>(input.focusSlots())) {
      rows.stream()
          .filter(r -> Objects.equals(r.getPlayerSlot(), slot))
          .sorted(Comparator.comparingInt(r -> position(entries, r.getEntryKey())))
          .map(r -> line(r, entries, BehaviourWording.hero(heroes, slot)))
          .flatMap(Optional::stream)
          .forEach(lines::add);
    }
    return lines.subList(0, Math.min(lines.size(), Math.max(1, input.maxFindings())));
  }

  private Optional<String> line(
      SkillEffectDomain row, List<SkillCatalogEntry> entries, String hero) {
    return SkillEffectWording.line(
        row.getEntryKey(),
        hero,
        skillName(row.getEntryKey(), entries),
        row.getCastCount() == null ? 0 : row.getCastCount(),
        figures(row));
  }

  private String skillName(String key, List<SkillCatalogEntry> entries) {
    return entries.stream()
        .filter(e -> e.key().equals(key) && !e.abilities().isEmpty())
        .findFirst()
        .map(e -> labels.label(e.abilities().get(0)))
        .filter(name -> name != null && !name.isBlank())
        .orElseGet(() -> key == null ? "" : key.replace('_', ' '));
  }

  private static int position(List<SkillCatalogEntry> entries, String key) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).key().equals(key)) {
        return i;
      }
    }
    return entries.size();
  }

  private Map<String, Integer> figures(SkillEffectDomain row) {
    if (row.getFigures() == null) {
      return Map.of();
    }
    try {
      return JSON.readValue(row.getFigures(), new TypeReference<Map<String, Integer>>() {});
    } catch (IOException e) {
      log.warn(
          "Match {}: unreadable skill effect figures of {}", row.getMatchId(), row.getEntryKey());
      return Map.of();
    }
  }
}
