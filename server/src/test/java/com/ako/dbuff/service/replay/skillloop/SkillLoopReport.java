package com.ako.dbuff.service.replay.skillloop;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitStage;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleNames;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import com.ako.dbuff.service.replay.report.MissedPickWording;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown: every stored missed pick and every stored
 * build fit verdict, in the words the match report uses.
 */
final class SkillLoopReport {
  static final String MISSED_PICKS = "Missed picks";
  static final String BUILD_FIT = "Build fit";

  private static final String ABILITY_DRAFT = "Ability Draft";
  private static final String NOT_AD =
      "not Ability Draft: skills are not drafted, so none is judged.";

  private static final String HOW_TO_READ =
      "\"Missed picks\" lists, per player, each skill the enemy took later that did clearly more"
          + " in the match than the one he took first, while it was still free. \"Build fit\""
          + " lists, per player and stage, the functions his role needs that his drafted skills"
          + " lack (missing), which of them the match backs (stated), the evidence and the"
          + " wording line of the report. Both are judged for Ability Draft only.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<MissedPickDomain> missedPicks,
      List<BuildFitDomain> buildFits,
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
    out.add("| player | stage | role | missing | stated | wording | assistant | owner |");
    out.add("|---|---|---|---|---|---|---|---|");
    d.buildFits().stream()
        .sorted(
            Comparator.comparing(BuildFitDomain::getPlayerSlot)
                .thenComparing(r -> BuildFitStage.valueOf(r.getStage())))
        .forEach(
            r ->
                out.add(
                    "| %s | %s | %s | %s | %s | %s | | |"
                        .formatted(
                            ReviewLoopReport.tableCell(hero(d, r.getPlayerSlot())),
                            r.getStage(),
                            RoleNames.words(r.getRole()),
                            functions(r.getMissing()),
                            functions(r.getStated()),
                            ReviewLoopReport.tableCell(r.getEvidence()))));
    return out;
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
