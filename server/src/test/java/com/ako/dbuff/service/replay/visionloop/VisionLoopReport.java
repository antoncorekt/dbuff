package com.ako.dbuff.service.replay.visionloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown: one row per important fight and team. It only
 * lays out what {@code fight_vision} holds; the table headed {@link #VISION} is what {@link
 * VisionLoopTopic} lets the loop compare between rounds. The verdict columns are left empty for the
 * reviewers.
 */
final class VisionLoopReport {
  static final String VISION = "Vision per important fight";
  static final String LIMITS = "What the data cannot tell";

  private static final String HOW_TO_READ =
      "One row per team for each important fight, in the window from 90 s before the fight to 15 s"
          + " after it; nothing else of the match is looked at. \"Wards near the fight\" are the"
          + " team's own observers (and sentries) within their vision radius of the fight's centre"
          + " when it began, with the age of the youngest; \"ward placed just before\" tells"
          + " whether an observer near the fight was placed within 30 s of its start (it counts"
          + " half). \"Invisible enemy heroes\" are the other team's heroes who were invisible"
          + " during the fight; \"detection\" is what this team had to see them (a sentry near,"
          + " dust used or held, a gem) and whether it used it. \"Wards destroyed\" are enemy wards"
          + " this team killed in the window, anywhere on the map. Tower, creep and hero vision are"
          + " not counted. Write OK or REJECT in the assistant and owner columns.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      List<LostFight> importantFights,
      List<FightVisionDomain> vision,
      Map<Long, String> heroNames,
      List<WardEventDomain> wards) {}

  private VisionLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d)));
    out.add("");
    out.addAll(table(d));
    out.add("");
    out.addAll(limits(d));
    return String.join("\n", out) + "\n";
  }

  private static String summary(MatchData d) {
    if (d.vision().isEmpty()) {
      return d.wards().isEmpty()
          ? "no ward log stored, so no vision rows."
          : "no vision rows stored (no important fight, or the analysis did not run).";
    }
    Map<VisionVerdict, Long> counts = new EnumMap<>(VisionVerdict.class);
    for (FightVisionDomain row : d.vision()) {
      for (VisionVerdict v : verdicts(row)) {
        counts.merge(v, 1L, Long::sum);
      }
    }
    return "%d important fights, %d team rows: %s."
        .formatted(
            d.importantFights().size(),
            d.vision().size(),
            Arrays.stream(VisionVerdict.values())
                .map(v -> "%s %d".formatted(v.getWords(), counts.getOrDefault(v, 0L)))
                .collect(Collectors.joining(", ")));
  }

  private static List<VisionVerdict> verdicts(FightVisionDomain row) {
    return Arrays.stream(row.getVerdict().split(","))
        .map(code -> Enums.find(VisionVerdict.class, code))
        .toList();
  }

  private static List<String> table(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + VISION,
                "",
                "| time window | team and fight | who lost | wards near the fight | ward placed"
                    + " just before | invisible enemy heroes | detection | wards destroyed |"
                    + " verdict | assistant | owner |",
                "|---|---|---|---|---|---|---|---|---|---|---|"));
    Map<Integer, LostFight> byFight =
        d.importantFights().stream()
            .collect(Collectors.toMap(l -> l.fight().getFightIndex(), l -> l, (a, b) -> a));
    List<FightVisionDomain> rows =
        d.vision().stream()
            .sorted(
                Comparator.comparing(FightVisionDomain::getFightIndex)
                    .thenComparing(FightVisionDomain::getTeam))
            .toList();
    for (FightVisionDomain row : rows) {
      LostFight lost = byFight.get(row.getFightIndex());
      FightDomain fight = lost == null ? null : lost.fight();
      out.add(
          "| %s | %s | %s | %s | %s | %s | %s | %s | %s |  |  |"
              .formatted(
                  "%s to %s"
                      .formatted(
                          LoopWording.clock(row.getWindowStart()),
                          LoopWording.clock(row.getWindowEnd())),
                  "%s, fight %d%s"
                      .formatted(
                          LoopWording.team(row.getTeam()),
                          row.getFightIndex(),
                          fight == null ? "" : " at " + LoopWording.clock(fight.getStartSeconds())),
                  lost == null ? "?" : LoopWording.team(lost.explanation().getLosingTeam()),
                  nearWards(row),
                  lateWard(row),
                  heroes(d, row.getInvisibleHeroes(), "none"),
                  detection(row),
                  destroyed(d, row),
                  ReviewLoopReport.tableCell(
                      verdicts(row).stream()
                          .map(VisionVerdict::getWords)
                          .collect(Collectors.joining("; ")))));
    }
    return out;
  }

  private static String nearWards(FightVisionDomain row) {
    String sentries =
        row.getActiveSentries() == 0 ? "" : ", %d sentry".formatted(row.getActiveSentries());
    if (row.getActiveObservers() == 0) {
      return "no observer" + sentries;
    }
    return "%d observer%s, youngest %s old%s"
        .formatted(
            row.getActiveObservers(),
            row.getActiveObservers() == 1 ? "" : "s",
            LoopWording.clock(row.getYoungestObserverAge()),
            sentries);
  }

  private static String lateWard(FightVisionDomain row) {
    return row.isLateWard() ? "yes, %d s before".formatted(row.getLateWardSeconds()) : "no";
  }

  private static String detection(FightVisionDomain row) {
    if (!row.isDetection()) {
      return "not available";
    }
    return row.isDetectionUsed() ? "available and used" : "available, not used";
  }

  private static String destroyed(MatchData d, FightVisionDomain row) {
    if (row.getWardsDestroyed() == 0) {
      return "none";
    }
    String by = heroes(d, row.getDestroyedBy(), "");
    return by.isEmpty()
        ? "" + row.getWardsDestroyed()
        : "%d by %s".formatted(row.getWardsDestroyed(), by);
  }

  private static String heroes(MatchData d, String slots, String none) {
    if (slots == null || slots.isBlank()) {
      return none;
    }
    return Arrays.stream(slots.split(","))
        .map(s -> LoopWording.hero(d.heroNames(), Long.parseLong(s)))
        .collect(Collectors.joining(", "));
  }

  private static List<String> limits(MatchData d) {
    long total = d.wards().size();
    long unknown =
        d.wards().stream().filter(w -> w.getRemovalKind() == WardRemovalKind.UNKNOWN).count();
    long aliveAtEnd =
        d.wards().stream()
            .filter(w -> w.getRemovedTime() == null && w.getPlacedTime() != null)
            .filter(
                w ->
                    d.match().getDuration() == null
                        || w.getPlacedTime() + lifetime(w) > d.match().getDuration())
            .count();
    List<String> out = new ArrayList<>();
    out.add("## " + LIMITS);
    out.add("");
    out.add(
        "- Ward log: %d wards, %d with no removal in the log (removal \"unknown\"), of which %d"
                .formatted(total, unknown, aliveAtEnd)
            + " were still within their life when the match ended. A ward with no logged removal is"
            + " taken to live its full time (observer 6 min, sentry 7 min).");
    out.add(
        "- Ward positions are OpenDota map cells, converted to world units as (cell - 128) x 128;"
            + " against the placing hero's track the median distance is about 620 units, 90"
            + " percent within about 920 (cast range and the 5 s track step).");
    out.add(
        "- Not counted as vision: towers, creeps, heroes and Roshan's pit; so a fight at a tower"
            + " may read as having no vision. Not read as detection: Oracle's Fortune's End and"
            + " other rare sources.");
    out.add(
        "- Invisible heroes come from the game's invisibility modifier and from the use of the"
            + " abilities and items in replay/invisible-heroes.json; invisibility the combat log"
            + " does not show is missed.");
    return out;
  }

  private static long lifetime(WardEventDomain w) {
    return w.getWardKind() == null
        ? WardKind.OBSERVER.getLifetimeSeconds()
        : w.getWardKind().getLifetimeSeconds();
  }
}
