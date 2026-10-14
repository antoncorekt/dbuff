package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The problems with a team's vision in the important fights: a summary of the fights where a team
 * had no ward covering the fight, then one line per fight and team with a problem (no ward, an
 * invisible enemy hero without detection, only a ward placed just before). The tracked players'
 * team comes first when it has problems, otherwise both teams are listed. When there are more than
 * {@code max-vision-lines}, the fights that moved the net worth most win, or the latest ones when
 * that is not known; the lines are listed in time order. Towers, creeps and nearby heroes are not
 * counted as vision, so "no vision" means no ward. It reads {@code fight_vision} (with the fights
 * for their clock and the fight importance for the swing), so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class VisionSection implements ReplayReportSection {
  private final FightVisionRepo visionRepo;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  private record Entry(FightVisionDomain row, FightDomain fight, FightImportanceDomain importance) {
    Double start() {
      return fight == null ? null : fight.getStartSeconds();
    }

    Long swing() {
      return importance == null ? null : importance.getGoldChange();
    }
  }

  private record Key(Integer fightIndex, String team) {}

  private static final Comparator<Entry> CHRONOLOGICAL =
      Comparator.comparing(Entry::start, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              e -> e.row().getFightIndex(), Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().getTeam(), Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

  private static final Comparator<Entry> BY_WORTH =
      Comparator.comparing(Entry::swing, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Entry::start, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(CHRONOLOGICAL);

  @Override
  public String name() {
    return "vision";
  }

  @Override
  public int order() {
    return 76;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<Entry> entries = entries(input.matchId(), visionRepo.findByMatchId(input.matchId()));
    List<Entry> problems = entries.stream().filter(e -> VisionWording.isProblem(e.row())).toList();
    if (problems.isEmpty()) {
      return List.of();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<Entry> shown = yoursOrBoth(problems, yours);
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    List<String> lines = new ArrayList<>();
    lines.add("**Vision**");
    lines.add(summary(entries));
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxBehaviourLineLength());
    BehaviourLines.Picked<Entry> picked =
        BehaviourLines.pick(shown, BY_WORTH, CHRONOLOGICAL, properties.getMaxVisionLines());
    picked
        .listed()
        .forEach(
            e ->
                lines.add(
                    LineText.cut(
                        "%s %s."
                            .formatted(time(e.start()), VisionWording.sentence(e.row(), heroes)),
                        max)));
    if (picked.leftOut() > 0) {
      lines.add(
          "and %d more vision %s"
              .formatted(picked.leftOut(), picked.leftOut() == 1 ? "problem" : "problems"));
    }
    return lines;
  }

  private static List<Entry> yoursOrBoth(List<Entry> problems, TeamSide yours) {
    if (yours == null) {
      return problems;
    }
    List<Entry> own =
        problems.stream().filter(e -> yours.name().equals(e.row().getTeam())).toList();
    return own.isEmpty() ? problems : own;
  }

  private List<Entry> entries(long matchId, List<FightVisionDomain> stored) {
    if (stored == null) {
      return List.of();
    }
    Map<Integer, FightDomain> fights =
        byFightIndex(
            fightRepo.findByMatchId(matchId), FightDomain::getFightIndex, FightDomain::getId);
    Map<Integer, FightImportanceDomain> importance =
        byFightIndex(
            importanceRepo.findByMatchId(matchId),
            FightImportanceDomain::getFightIndex,
            FightImportanceDomain::getId);
    return stored.stream()
        .filter(Objects::nonNull)
        .collect(
            Collectors.toMap(
                r -> new Key(r.getFightIndex(), r.getTeam()),
                Function.identity(),
                BinaryOperator.maxBy(
                    Comparator.comparing(
                        FightVisionDomain::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))))
        .values()
        .stream()
        .map(
            row ->
                new Entry(
                    row, fights.get(row.getFightIndex()), importance.get(row.getFightIndex())))
        .toList();
  }

  /** One row per fight index: the one with the largest id, so the repository order never counts. */
  private static <T> Map<Integer, T> byFightIndex(
      Collection<T> rows, Function<T, Integer> index, Function<T, Long> id) {
    if (rows == null) {
      return new HashMap<>();
    }
    return rows.stream()
        .filter(Objects::nonNull)
        .filter(r -> index.apply(r) != null)
        .collect(
            Collectors.toMap(
                index,
                Function.identity(),
                BinaryOperator.maxBy(
                    Comparator.comparing(id, Comparator.nullsFirst(Comparator.naturalOrder())))));
  }

  private static String summary(List<Entry> entries) {
    Set<Integer> fights = new HashSet<>();
    Set<Integer> withoutVision = new HashSet<>();
    Map<String, Long> perTeam =
        entries.stream()
            .filter(e -> VisionWording.hadNoVision(e.row()))
            .collect(
                Collectors.groupingBy(
                    e -> Prettify.team(e.row().getTeam()), Collectors.counting()));
    entries.forEach(
        e -> {
          fights.add(e.row().getFightIndex());
          if (VisionWording.hadNoVision(e.row())) {
            withoutVision.add(e.row().getFightIndex());
          }
        });
    String text =
        "Fights where a team had no vision: %d of %d important fights"
            .formatted(withoutVision.size(), fights.size());
    if (perTeam.isEmpty()) {
      return text;
    }
    return text
        + " ("
        + perTeam.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> "%s %d".formatted(e.getKey(), e.getValue()))
            .collect(Collectors.joining(", "))
        + ")";
  }

  private static String time(Double seconds) {
    return seconds == null ? "?" : GameTime.clock(seconds);
  }
}
