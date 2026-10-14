package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveKind;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveResult;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Where a won important fight was not turned into an objective: a summary of how many were, then a
 * line for each missed one (the fights that moved the gold most come first when there are more than
 * {@code max-objective-lines}; listed in time order) and one sentence about Roshan when he was
 * taken after a won fight. A match with no important fights prints nothing. It reads {@code
 * objective_conversion} (with the fights for their clock and the fight importance for the gold
 * swing), so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class ObjectivesSection implements ReplayReportSection {
  private final ObjectiveConversionRepo objectiveRepo;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;
  private final BehaviourProperties behaviour;

  /** The rows with what the section needs to know about their fight. */
  private record Entry(
      ObjectiveConversionDomain row, FightDomain fight, FightImportanceDomain importance) {
    Double start() {
      return fight == null ? null : fight.getStartSeconds();
    }

    Long swing() {
      return importance == null ? null : importance.getGoldChange();
    }
  }

  private static final Comparator<Entry> CHRONOLOGICAL =
      Comparator.comparing(Entry::start, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              e -> e.row().getFightIndex(), Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

  private static final Comparator<Entry> BY_WORTH =
      Comparator.comparing(Entry::swing, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Entry::start, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(CHRONOLOGICAL);

  @Override
  public String name() {
    return "objectives";
  }

  @Override
  public int order() {
    return 74;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<ObjectiveConversionDomain> stored = objectiveRepo.findByMatchId(input.matchId());
    List<Entry> entries = entries(input.matchId(), stored);
    if (entries.isEmpty()) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> lines = new ArrayList<>();
    lines.add("**Objectives**");
    lines.add(summary(entries));
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxBehaviourLineLength());
    BehaviourLines.pick(
            entries.stream().filter(e -> result(e) == ObjectiveResult.MISSED).toList(),
            BY_WORTH,
            CHRONOLOGICAL,
            properties.getMaxObjectiveLines())
        .forEach(e -> lines.add(LineText.cut(missed(e, heroes, yours), max)));
    entries.stream()
        .filter(ObjectivesSection::tookRoshan)
        .max(CHRONOLOGICAL)
        .ifPresent(e -> lines.add(LineText.cut(roshan(e, heroes, yours), max)));
    return lines;
  }

  private List<Entry> entries(long matchId, List<ObjectiveConversionDomain> stored) {
    if (stored == null) {
      return List.of();
    }
    Map<Integer, FightDomain> fights =
        byFightIndex(fightRepo.findByMatchId(matchId), f -> f.getFightIndex());
    Map<Integer, FightImportanceDomain> importance =
        byFightIndex(importanceRepo.findByMatchId(matchId), f -> f.getFightIndex());
    return stored.stream()
        .filter(Objects::nonNull)
        .map(
            row ->
                new Entry(
                    row, fights.get(row.getFightIndex()), importance.get(row.getFightIndex())))
        .filter(e -> result(e) != null)
        .toList();
  }

  private static <T> Map<Integer, T> byFightIndex(Collection<T> rows, Function<T, Integer> index) {
    if (rows == null) {
      return new HashMap<>();
    }
    return rows.stream()
        .filter(Objects::nonNull)
        .filter(r -> index.apply(r) != null)
        .collect(Collectors.toMap(index, Function.identity(), (a, b) -> a));
  }

  private static ObjectiveResult result(Entry e) {
    return Enums.find(ObjectiveResult.class, e.row().getResult());
  }

  private static boolean tookRoshan(Entry e) {
    return result(e) == ObjectiveResult.CONVERTED
        && Enums.find(ObjectiveKind.class, e.row().getObjectiveKind()) == ObjectiveKind.ROSHAN;
  }

  private static String summary(List<Entry> entries) {
    long converted = entries.stream().filter(e -> result(e) == ObjectiveResult.CONVERTED).count();
    return "Won fights turned into objectives: %d of %d".formatted(converted, entries.size());
  }

  private String missed(Entry e, Map<Long, String> heroes, TeamSide yours) {
    ObjectiveConversionDomain r = e.row();
    String head =
        "%s %s won the fight but did not take an objective in the next %d seconds"
            .formatted(time(e.start()), winner(r, yours), behaviour.getObjectiveWindowSeconds());
    if (r.getNearestTower() == null) {
      return head + "; " + ObjectiveWording.noTowerReason(r) + ".";
    }
    return "%s; the nearest %s tower still standing was %s and %s, the winner's closest hero, %s."
        .formatted(
            head,
            enemy(r.getWinnerTeam()),
            ObjectiveWording.tower(r.getNearestTower(), r.getTowerLane()),
            BehaviourWording.hero(heroes, r.getNearestHeroSlot()),
            standing(r));
  }

  private String standing(ObjectiveConversionDomain r) {
    String where = BehaviourWording.place(r.getHeroZone(), r.getHeroLane());
    if (r.getTowerDistance() == null) {
      return "stood in the %s, how far from it is not known".formatted(where);
    }
    double distance = r.getTowerDistance();
    String how =
        distance <= properties.getObjectiveCloseDistance()
            ? "close to it"
            : distance >= properties.getObjectiveFarDistance()
                ? "far from it"
                : "a fair way from it";
    return "stood in the %s, %s".formatted(where, how);
  }

  private static String roshan(Entry e, Map<Long, String> heroes, TeamSide yours) {
    return "After the won fight at %s %s took Roshan: %s."
        .formatted(
            time(e.start()), winner(e.row(), yours), ObjectiveWording.roshanNote(e.row(), heroes));
  }

  private static String winner(ObjectiveConversionDomain r, TeamSide yours) {
    TeamSide side = Enums.find(TeamSide.class, r.getWinnerTeam());
    return Prettify.team(r.getWinnerTeam()) + (side != null && side == yours ? " (your team)" : "");
  }

  private static String enemy(String winner) {
    TeamSide side = Enums.find(TeamSide.class, winner);
    return side == null ? "enemy" : Prettify.team(side.opponent().name());
  }

  private static String time(Double seconds) {
    return seconds == null ? "?" : GameTime.clock(seconds);
  }
}
