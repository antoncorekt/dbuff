package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatMath;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatWording;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The most useful lines about each focus player's skill picks, from the stored skill statistics,
 * build fit and missed picks only: the best missed pick, the first stated build gap, and the
 * statistics furthest from a coin flip fill the rest. The ranking uses the Wilson lower bound, so a
 * tiny sample never sorts above a solid one, and no sample is left out for being small.
 */
@Component
@RequiredArgsConstructor
public class SkillPickFindings {
  private static final double EVEN = 0.5;

  private final SkillPickStatRepo statRepo;
  private final BuildFitRepo buildRepo;
  private final MissedPickRepo missedRepo;
  private final SkillStatWording statWording;
  private final AbilityLabels labels;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  public List<Finding> find(ReplayReportInput input) {
    List<SkillPickStatDomain> stats = nonNull(statRepo.findByMatchId(input.matchId()));
    List<BuildFitDomain> builds = nonNull(buildRepo.findByMatchId(input.matchId()));
    List<MissedPickDomain> missed = nonNull(missedRepo.findByMatchId(input.matchId()));
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    int limit = Math.max(1, input.maxFindings());
    List<Finding> findings = new ArrayList<>();
    for (Long slot : new TreeSet<>(input.focusSlots())) {
      String who = who(input, heroes, slot);
      List<Finding> own = new ArrayList<>();
      missed.stream()
          .filter(m -> Objects.equals(m.getPlayerSlot(), slot))
          .max(Comparator.comparingDouble(SkillPickFindings::missedStrength))
          .ifPresent(
              m -> own.add(finding(FindingKind.MISSED, who, MissedPickWording.line(m, labels))));
      builds.stream()
          .filter(b -> Objects.equals(b.getPlayerSlot(), slot) && stated(b))
          .findFirst()
          .ifPresent(b -> own.add(finding(FindingKind.BUILD, who, b.getEvidence().strip())));
      stats.stream()
          .filter(s -> Objects.equals(s.getPlayerSlot(), slot) && s.getGames() > 0)
          .sorted(Comparator.comparingDouble(SkillPickFindings::statStrength).reversed())
          .limit(Math.max(0, limit - own.size()))
          .forEach(s -> own.add(finding(FindingKind.PICK, who, statWording.describe(s))));
      findings.addAll(own.subList(0, Math.min(limit, own.size())));
    }
    return findings;
  }

  private static boolean stated(BuildFitDomain row) {
    return row.getStated() != null
        && !row.getStated().isBlank()
        && row.getEvidence() != null
        && !row.getEvidence().isBlank();
  }

  private Finding finding(FindingKind kind, String who, String text) {
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxSkillPickLineLength());
    return new Finding(kind, LineText.cut(who + ": " + text, max));
  }

  private static String who(ReplayReportInput input, Map<Long, String> heroes, Long slot) {
    String hero = heroes.get(slot);
    String name = input.namesBySlot() == null ? null : input.namesBySlot().get(slot);
    if (name == null || name.isBlank()) {
      return BehaviourWording.hero(heroes, slot);
    }
    return ReportHeroNames.label(name, hero);
  }

  /** How far from an even win rate the row is with confidence: negative when it could be chance. */
  static double statStrength(SkillPickStatDomain row) {
    long games = row.getGames();
    double strong = SkillStatMath.wilsonLowerBound(row.getWins(), games) - EVEN;
    double weak = SkillStatMath.wilsonLowerBound(games - row.getWins(), games) - EVEN;
    return Math.max(strong, weak);
  }

  private static double missedStrength(MissedPickDomain row) {
    return row.getRatio() == null ? 0 : row.getRatio();
  }

  private static <T> List<T> nonNull(List<T> rows) {
    return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
  }
}
