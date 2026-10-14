package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveKind;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveResult;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.ObjectiveWording;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The objectives table of the behaviour report; it only lays out what {@code objective_conversion}
 * holds. The owner's verdict column is left empty.
 */
final class BehaviourObjectiveSection {
  static final String OBJECTIVES = "Objectives after fights";

  private static final String HOW_TO_READ =
      "Objectives after fights: for each important fight (from the fight importance table) that a"
          + " team won, did the winner take an enemy tower, barracks or the ancient, or kill"
          + " Roshan, from the start of the fight to %d s after it ended? Converted means it did"
          + " (the first such objective is told, with the seconds after the fight ended; 0 means"
          + " it fell during the fight); missed means nothing of that, and then the enemy lane"
          + " tower still standing that was closest to one of the winner's living heroes when the"
          + " fight ended is named, with how many map units that hero was from it (a hero walks"
          + " roughly 300 units a second) and where he stood. That is not a claim that the tower"
          + " was undefended, which is not known. For Roshan, the winner's heroes within %d units"
          + " of the hero who killed him are told, and whether the winner was ahead (expected) or"
          + " behind (a gamble) before the fight. A fight that ended so close to the end of the"
          + " game that the window could not run out, with nothing taken, is left out. Times are"
          + " the game clock (m:ss). Write OK or REJECT in the owner verdict column.";

  private BehaviourObjectiveSection() {}

  static String howToRead(BehaviourProperties p) {
    return HOW_TO_READ.formatted(p.getObjectiveWindowSeconds(), p.getRoshanNearRadius());
  }

  static String summary(List<ObjectiveConversionDomain> rows) {
    if (rows.isEmpty()) {
      return "no objective conversions stored";
    }
    String kinds =
        Arrays.stream(ObjectiveKind.values())
            .map(
                k ->
                    "%d %s"
                        .formatted(
                            count(rows, ObjectiveConversionDomain::getObjectiveKind, k.name()),
                            k.words()))
            .collect(Collectors.joining(", "));
    return "%d won important %s: %d converted (%s), %d missed"
        .formatted(
            rows.size(),
            rows.size() == 1 ? "fight" : "fights",
            count(rows, ObjectiveConversionDomain::getResult, ObjectiveResult.CONVERTED.name()),
            kinds,
            count(rows, ObjectiveConversionDomain::getResult, ObjectiveResult.MISSED.name()));
  }

  static List<String> table(
      List<ObjectiveConversionDomain> rows,
      List<FightDomain> fights,
      Map<Long, String> heroNames,
      BehaviourProperties properties) {
    Map<Integer, FightDomain> byIndex =
        fights.stream()
            .filter(f -> f.getFightIndex() != null)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + OBJECTIVES,
                "",
                "| fight | who won | result | what happened | owner verdict |",
                "|---|---|---|---|---|"));
    rows.stream()
        .sorted(Comparator.comparing(ObjectiveConversionDomain::getFightIndex))
        .forEach(
            r ->
                out.add(
                    "| %s | %s | %s | %s | |"
                        .formatted(
                            fightCell(byIndex.get(r.getFightIndex())),
                            LoopWording.team(r.getWinnerTeam()),
                            LoopWording.words(r.getResult()),
                            ReviewLoopReport.tableCell(happened(r, heroNames, properties)))));
    return out;
  }

  private static long count(
      List<ObjectiveConversionDomain> rows,
      Function<ObjectiveConversionDomain, String> column,
      String value) {
    return rows.stream().filter(r -> value.equals(column.apply(r))).count();
  }

  private static String fightCell(FightDomain fight) {
    if (fight == null) {
      return "?";
    }
    return "Fight at %s to %s"
        .formatted(
            LoopWording.clock(fight.getStartSeconds()), LoopWording.clock(fight.getEndSeconds()));
  }

  private static String happened(
      ObjectiveConversionDomain r, Map<Long, String> heroNames, BehaviourProperties properties) {
    String winner = LoopWording.team(r.getWinnerTeam());
    ObjectiveKind kind = Enums.find(ObjectiveKind.class, r.getObjectiveKind());
    if (kind == null) {
      return missed(r, heroNames, properties);
    }
    String when =
        r.getSecondsAfter() == null || r.getSecondsAfter() == 0
            ? "during the fight"
            : "%d s after it ended".formatted(r.getSecondsAfter());
    if (kind == ObjectiveKind.ROSHAN) {
      return "%s killed Roshan %s; %s"
          .formatted(winner, when, ObjectiveWording.roshanNote(r, heroNames));
    }
    return "%s took %s %s".formatted(winner, building(r, kind), when);
  }

  private static String building(ObjectiveConversionDomain r, ObjectiveKind kind) {
    Optional<BuildingName> name = BuildingName.parse(r.getObjectiveKey());
    if (kind == ObjectiveKind.ANCIENT || name.isEmpty()) {
      return kind.words();
    }
    return "the %s%s"
        .formatted(
            kind == ObjectiveKind.TOWER
                ? "tier %d tower".formatted(name.get().tier())
                : barracks(name.get()),
            name.get().lane() == null
                ? ""
                : " in the " + LoopWording.words(name.get().lane()) + " lane");
  }

  private static String barracks(BuildingName name) {
    return name.kind().endsWith("MELEE") ? "melee barracks" : "ranged barracks";
  }

  private static String missed(
      ObjectiveConversionDomain r, Map<Long, String> heroNames, BehaviourProperties properties) {
    String took = "took nothing in %d s".formatted(properties.getObjectiveWindowSeconds());
    if (r.getNearestTower() == null) {
      return "%s took nothing in %d s; %s"
          .formatted(
              LoopWording.team(r.getWinnerTeam()),
              properties.getObjectiveWindowSeconds(),
              ObjectiveWording.noTowerReason(r));
    }
    String tier =
        BuildingName.parse(r.getNearestTower())
            .map(t -> "tier " + t.tier())
            .orElse(r.getNearestTower());
    String lane =
        r.getTowerLane() == null ? "" : " in the " + LoopWording.words(r.getTowerLane()) + " lane";
    return "%s %s; nearest enemy tower still standing: %s%s, %d units from %s (%s) at the end of the fight, %d of the winners alive"
        .formatted(
            LoopWording.team(r.getWinnerTeam()),
            took,
            tier,
            lane,
            r.getTowerDistance(),
            LoopWording.hero(heroNames, r.getNearestHeroSlot()),
            LoopWording.place(r.getHeroZone(), r.getHeroLane()),
            r.getWinnersAlive());
  }
}
