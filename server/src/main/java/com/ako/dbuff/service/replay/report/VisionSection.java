package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The problems with a team's vision in the important fights: a team with no ward covering the fight
 * is a problem only when the other team in the same fight had vision (a contrast); an invisible
 * enemy hero without detection and a ward placed just before the fight are problems on their own.
 * One line per fight and team, the tracked players' team first when it has problems, otherwise both
 * teams; when there are more than {@code max-vision-lines}, the fights with the largest net worth
 * swing win, the tracked team's rows and the worse problems before others; the lines are listed in
 * time order. Towers, creeps and nearby heroes are not counted as vision, so "no vision" means no
 * ward. It reads {@code fight_vision} (with the fights for their clock and the fight importance for
 * the swing), so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class VisionSection implements ReplayReportSection {
  private final FightVisionRepo visionRepo;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  /** A stored row with its fight, its parsed verdicts and the verdicts that count as problems. */
  private record Entry(
      FightVisionDomain row,
      TeamSide team,
      FightDomain fight,
      FightImportanceDomain importance,
      List<VisionVerdict> verdicts,
      List<VisionVerdict> problems) {
    Double start() {
      return fight == null ? null : fight.getStartSeconds();
    }

    Long swing() {
      return importance == null || importance.getGoldChange() == null
          ? null
          : Math.abs(importance.getGoldChange());
    }

    boolean contrast() {
      return problems.contains(VisionVerdict.NO_VISION);
    }

    int severity() {
      return problems.stream().mapToInt(Enum::ordinal).min().orElse(Integer.MAX_VALUE);
    }
  }

  private record Key(Integer fightIndex, String team) {}

  private static final Comparator<Entry> CHRONOLOGICAL =
      Comparator.comparing(Entry::start, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().getFightIndex())
          .thenComparing(e -> e.row().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

  private static Comparator<Entry> byWorth(TeamSide yours) {
    return Comparator.comparing(Entry::swing, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(e -> e.team() == yours ? 0 : 1)
        .thenComparingInt(Entry::severity)
        .thenComparing(Entry::start, Comparator.nullsLast(Comparator.<Double>reverseOrder()))
        .thenComparing(CHRONOLOGICAL);
  }

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
    List<Entry> problems = entries.stream().filter(e -> !e.problems().isEmpty()).toList();
    if (problems.isEmpty()) {
      return List.of();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<Entry> own = problems.stream().filter(e -> e.team() == yours).toList();
    boolean ownView = yours != null && !own.isEmpty();
    List<Entry> shown = ownView ? own : problems;
    String scope = yours == null ? "" : ownView ? " for your team" : " for both teams";
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    List<String> lines = new ArrayList<>();
    lines.add("**Vision**");
    summary(entries, shown, scope).ifPresent(lines::add);
    neither(entries).ifPresent(lines::add);
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxBehaviourLineLength());
    BehaviourLines.Picked<Entry> picked =
        BehaviourLines.pick(shown, byWorth(yours), CHRONOLOGICAL, properties.getMaxVisionLines());
    picked
        .listed()
        .forEach(
            e ->
                lines.add(
                    LineText.cut(
                        "%s %s."
                            .formatted(
                                time(e.start()),
                                VisionWording.sentence(e.row(), e.problems(), heroes)),
                        max)));
    if (picked.leftOut() > 0) {
      lines.add(
          "and %d more vision %s%s"
              .formatted(picked.leftOut(), picked.leftOut() == 1 ? "problem" : "problems", scope));
    }
    return lines;
  }

  private List<Entry> entries(long matchId, List<FightVisionDomain> stored) {
    if (stored == null) {
      return List.of();
    }
    Map<Key, FightVisionDomain> rows =
        latestById(
            stored.stream().filter(VisionSection::placeable).toList(),
            r -> new Key(r.getFightIndex(), r.getTeam()),
            FightVisionDomain::getId);
    Map<Integer, FightDomain> fights =
        latestById(
            fightRepo.findByMatchId(matchId), FightDomain::getFightIndex, FightDomain::getId);
    Map<Integer, FightImportanceDomain> importance =
        latestById(
            importanceRepo.findByMatchId(matchId),
            FightImportanceDomain::getFightIndex,
            FightImportanceDomain::getId);
    Map<Key, List<VisionVerdict>> verdicts = new HashMap<>();
    rows.forEach((key, row) -> verdicts.put(key, VisionWording.verdicts(row)));
    return rows.entrySet().stream()
        .map(
            e -> {
              FightVisionDomain row = e.getValue();
              TeamSide team = Enums.find(TeamSide.class, row.getTeam());
              Key opponent = new Key(row.getFightIndex(), team.opponent().name());
              return new Entry(
                  row,
                  team,
                  fights.get(row.getFightIndex()),
                  importance.get(row.getFightIndex()),
                  verdicts.get(e.getKey()),
                  problems(verdicts.get(e.getKey()), rows.get(opponent), verdicts.get(opponent)));
            })
        .toList();
  }

  private static boolean placeable(FightVisionDomain row) {
    return row != null
        && row.getFightIndex() != null
        && Enums.find(TeamSide.class, row.getTeam()) != null;
  }

  private static List<VisionVerdict> problems(
      List<VisionVerdict> verdicts,
      FightVisionDomain opponent,
      List<VisionVerdict> opponentVerdicts) {
    return verdicts.stream()
        .filter(v -> v != VisionVerdict.FINE)
        .filter(
            v ->
                v != VisionVerdict.NO_VISION
                    || (opponent != null && hadVision(opponent, opponentVerdicts)))
        .toList();
  }

  private static boolean hadVision(FightVisionDomain row, List<VisionVerdict> verdicts) {
    return !verdicts.contains(VisionVerdict.NO_VISION)
        || positive(row.getActiveObservers())
        || positive(row.getActiveSentries());
  }

  private static boolean positive(Integer count) {
    return count != null && count > 0;
  }

  /** One row per key: the one with the largest id, so the repository order never counts. */
  private static <K, T> Map<K, T> latestById(
      Collection<T> rows, Function<T, K> key, Function<T, Long> id) {
    if (rows == null) {
      return new HashMap<>();
    }
    return rows.stream()
        .filter(Objects::nonNull)
        .filter(r -> key.apply(r) != null)
        .collect(
            Collectors.toMap(
                key,
                Function.identity(),
                BinaryOperator.maxBy(
                    Comparator.comparing(id, Comparator.nullsFirst(Comparator.naturalOrder())))));
  }

  private static Optional<String> summary(List<Entry> all, List<Entry> shown, String scope) {
    List<Entry> contrasts = shown.stream().filter(Entry::contrast).toList();
    Set<Integer> fights =
        contrasts.stream().map(e -> e.row().getFightIndex()).collect(Collectors.toSet());
    if (fights.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Long> perTeam =
        contrasts.stream()
            .collect(
                Collectors.groupingBy(
                    e -> Prettify.team(e.row().getTeam()), TreeMap::new, Collectors.counting()));
    String counts =
        perTeam.size() < 2
            ? ""
            : perTeam.entrySet().stream()
                .map(e -> "%s %d".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", ", " (", ")"));
    return Optional.of(
        "Fights where only one team had vision: %d of %d important fights%s%s"
            .formatted(fights.size(), importantFights(all), counts, scope));
  }

  private static long importantFights(List<Entry> all) {
    return all.stream().map(e -> e.row().getFightIndex()).distinct().count();
  }

  private static Optional<String> neither(List<Entry> all) {
    Map<Integer, List<Entry>> byFight =
        all.stream().collect(Collectors.groupingBy(e -> e.row().getFightIndex()));
    long neither =
        byFight.values().stream()
            .filter(rows -> rows.size() == 2)
            .filter(rows -> rows.stream().noneMatch(e -> hadVision(e.row(), e.verdicts())))
            .count();
    long total = importantFights(all);
    return neither * 2 > total
        ? Optional.of(
            "Neither team had ward coverage in %d of %d important fights".formatted(neither, total))
        : Optional.empty();
  }

  private static String time(Double seconds) {
    return seconds == null ? "?" : GameTime.clock(seconds);
  }
}
