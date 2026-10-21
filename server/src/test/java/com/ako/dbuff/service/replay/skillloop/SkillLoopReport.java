package com.ako.dbuff.service.replay.skillloop;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitStage;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleNames;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import com.ako.dbuff.service.replay.report.MissedPickWording;
import com.ako.dbuff.service.replay.report.Prettify;
import com.ako.dbuff.service.replay.report.SkillEffectWording;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown: every stored missed pick and every stored
 * build fit verdict, in the words the match report uses.
 */
final class SkillLoopReport {
  static final String MISSED_PICKS = "Missed picks";
  static final String BUILD_FIT = "Build fit";
  static final String SKILL_EFFECTS = "Skill effects";

  private static final int MAX_DETAIL_LINES = 40;
  private static final int SECONDS_PER_MINUTE = 60;
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String ABILITY_DRAFT = "Ability Draft";
  private static final String NOT_AD =
      "not Ability Draft: skills are not drafted, so none is judged.";

  private static final String HOW_TO_READ =
      "\"Missed picks\" lists, per player, each skill the enemy took later that did clearly more"
          + " in the match than the one he took first, while it was still free. \"Build fit\""
          + " lists, per player and stage, the functions his role needs that his drafted skills"
          + " lack (missing), the verdict (FIT, GAPS_NOT_SHOWN or GAP), which of them the match backs (stated), the functions with no value (unknown), the evidence and the"
          + " wording line of the report. Both are judged for Ability Draft only. \"Skill effects\" lists,"
          + " per hero and catalogued skill, how many casts ended in each outcome and every cast"
          + " (up to 40) with its numbers, after the line the match report would say.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<MissedPickDomain> missedPicks,
      List<BuildFitDomain> buildFits,
      List<SkillEffectDomain> skillEffects,
      AbilityLabels labels) {
    boolean abilityDraft() {
      return ABILITY_DRAFT.equals(modeName);
    }
  }

  private SkillLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d)));
    out.add("");
    out.addAll(missedPicks(d));
    out.add("");
    out.addAll(buildFit(d));
    out.add("");
    out.addAll(skillEffects(d));
    return String.join("\n", out) + "\n";
  }

  private static String summary(MatchData d) {
    if (!d.abilityDraft()) {
      return NOT_AD;
    }
    return "%d missed picks, %d build fit rows."
        .formatted(d.missedPicks().size(), d.buildFits().size());
  }

  private static List<String> missedPicks(MatchData d) {
    List<String> out = new ArrayList<>(List.of("## " + MISSED_PICKS, ""));
    if (!d.abilityDraft()) {
      out.add(NOT_AD);
      return out;
    }
    out.add("Total: " + d.missedPicks().size());
    out.add("");
    out.add("| player | wording | assistant | owner |");
    out.add("|---|---|---|---|");
    d.missedPicks().stream()
        .sorted(
            Comparator.comparing(MissedPickDomain::getPlayerSlot)
                .thenComparing(MissedPickDomain::getTakenTurn)
                .thenComparing(MissedPickDomain::getCandidateTurn))
        .forEach(
            r ->
                out.add(
                    "| %s | %s | | |"
                        .formatted(
                            ReviewLoopReport.tableCell(hero(d, r.getPlayerSlot())),
                            ReviewLoopReport.tableCell(MissedPickWording.line(r, d.labels())))));
    return out;
  }

  private static List<String> buildFit(MatchData d) {
    List<String> out = new ArrayList<>(List.of("## " + BUILD_FIT, ""));
    if (!d.abilityDraft()) {
      out.add(NOT_AD);
      return out;
    }
    out.add(
        "| player | stage | role | verdict | missing | stated | unknown | wording | assistant | owner |");
    out.add("|---|---|---|---|---|---|---|---|---|---|");
    d.buildFits().stream()
        .sorted(
            Comparator.comparing(BuildFitDomain::getPlayerSlot)
                .thenComparing(r -> BuildFitStage.valueOf(r.getStage())))
        .forEach(
            r ->
                out.add(
                    "| %s | %s | %s | %s | %s | %s | %s | %s | | |"
                        .formatted(
                            ReviewLoopReport.tableCell(hero(d, r.getPlayerSlot())),
                            r.getStage(),
                            RoleNames.words(r.getRole()),
                            r.getVerdict() == null ? "old" : r.getVerdict(),
                            functions(r.getMissing()),
                            functions(r.getStated()),
                            functions(r.getUnknown()),
                            ReviewLoopReport.tableCell(r.getEvidence()))));
    return out;
  }

  private static List<String> skillEffects(MatchData d) {
    List<String> out = new ArrayList<>(List.of("## " + SKILL_EFFECTS, ""));
    if (d.skillEffects().isEmpty()) {
      out.add("No catalogued skill was cast or learned.");
      return out;
    }
    d.skillEffects().stream()
        .sorted(
            Comparator.comparing(SkillEffectDomain::getPlayerSlot)
                .thenComparing(SkillEffectDomain::getEntryKey))
        .forEach(
            r -> {
              out.add(
                  "- %s, %s: %d casts; %s"
                      .formatted(
                          hero(d, r.getPlayerSlot()),
                          r.getEntryKey(),
                          r.getCastCount(),
                          counts(r.getFigures())));
              reportLine(d, r).ifPresent(line -> out.add("  - Report line: " + line));
              detailLines(r.getDetails()).forEach(line -> out.add("  - " + line));
            });
    return out;
  }

  private static Optional<String> reportLine(MatchData d, SkillEffectDomain r) {
    return SkillEffectWording.line(
        r.getEntryKey(),
        hero(d, r.getPlayerSlot()),
        Prettify.ability(r.getEntryKey()),
        r.getCastCount() == null ? 0 : r.getCastCount(),
        read(
            r.getFigures(),
            new TypeReference<Map<String, Integer>>() {},
            Map.<String, Integer>of()));
  }

  private static String counts(String figures) {
    return read(figures, new TypeReference<Map<String, Integer>>() {}, Map.<String, Integer>of())
        .entrySet()
        .stream()
        .map(e -> e.getKey() + " " + e.getValue())
        .collect(Collectors.joining(", "));
  }

  private static List<String> detailLines(String details) {
    List<Map<String, Object>> rows =
        read(details, new TypeReference<List<Map<String, Object>>>() {}, List.of());
    List<String> lines =
        rows.stream()
            .limit(MAX_DETAIL_LINES)
            .map(
                row -> {
                  int second = (int) Math.round(((Number) row.get("second")).doubleValue());
                  String numbers =
                      ((Map<?, ?>) row.get("numbers"))
                          .entrySet().stream()
                              .map(e -> e.getKey() + " " + shorten(e.getValue()))
                              .collect(Collectors.joining(", "));
                  return "%s %d:%02d on %s (%s)"
                      .formatted(
                          row.get("outcome"),
                          Math.floorDiv(second, SECONDS_PER_MINUTE),
                          Math.floorMod(second, SECONDS_PER_MINUTE),
                          row.get("target"),
                          numbers);
                })
            .collect(Collectors.toCollection(ArrayList::new));
    if (rows.size() > MAX_DETAIL_LINES) {
      lines.add("... %d more casts".formatted(rows.size() - MAX_DETAIL_LINES));
    }
    return lines;
  }

  private static String shorten(Object value) {
    return value instanceof Number n
        ? String.valueOf(Math.round(n.doubleValue()))
        : String.valueOf(value);
  }

  private static <T> T read(String json, TypeReference<T> type, T fallback) {
    try {
      return json == null ? fallback : JSON.readValue(json, type);
    } catch (java.io.IOException e) {
      return fallback;
    }
  }

  private static String hero(MatchData d, Long slot) {
    return d.heroNames().getOrDefault(slot, "slot " + slot);
  }

  private static String functions(String names) {
    return names == null || names.isBlank()
        ? "none"
        : Arrays.stream(names.split(","))
            .map(n -> RoleFunction.valueOf(n.trim()).words())
            .collect(Collectors.joining(", "));
  }
}
