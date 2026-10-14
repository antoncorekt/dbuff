package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.reason.ReasonScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The order in which the reasons of one fight are told, computed on read so a change of rule needs
 * no re-analysis. By severity, which weighs role and presence only (never hero gold); team reasons
 * come after the player reasons unless no player reason is severe enough to stand on its own; that
 * decision looks at the ranked entries only, so an unranked early cooldown reason never makes a
 * player lead. The absence reasons of one hero are one entry, draft-level reasons come after every
 * other, and the cooldown reasons of an early fight are not ranked at all.
 */
public final class ReasonRanking {
  /** Why one hero was away from a fight; of these, one entry per hero is ranked. */
  public static final Set<String> ABSENCE_FAMILY =
      Set.of(
          "NO_TP",
          "TP_ON_COOLDOWN",
          "HAD_TP_NOT_USED",
          "CARRY_FARMING_ELSEWHERE",
          "SUPPORT_ABSENT");

  /** What the draft promised and the fight did not show; never above a reason of what happened. */
  public static final Set<String> DRAFT_LEVEL =
      Set.of("INTENT_NOT_PLAYED", "WEAK_COMBINATION", "STRONG_SKILL_LEFT");

  /** Cooldowns say little in a fight that starts before the heroes have their items. */
  public static final Set<String> COOLDOWN = Set.of("ABILITY_ON_COOLDOWN", "ITEM_ON_COOLDOWN");

  /**
   * @param reason the entry's main reason
   * @param also the codes of the other reasons it stands for, strongest first
   */
  public record Entry(FightReasonDomain reason, List<String> also) {}

  /**
   * @param ranked the entries in rank order
   * @param notRanked the stored reasons left out of the ranking, in storage order
   */
  public record Ranking(List<Entry> ranked, List<FightReasonDomain> notRanked) {}

  /** Total, so equal reasons never fall back on the order the repository returned them in. */
  private static final Comparator<FightReasonDomain> STRONGEST_FIRST =
      Comparator.comparingDouble(ReasonRanking::severity)
          .reversed()
          .thenComparing(
              FightReasonDomain::getScope, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              FightReasonDomain::getSubjectSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              FightReasonDomain::getCode, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              FightReasonDomain::getEvidence, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(FightReasonDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private ReasonRanking() {}

  /**
   * @param fightStartSeconds null when unknown, which counts as not early
   */
  public static Ranking rank(
      List<FightReasonDomain> reasons, Double fightStartSeconds, FightLossProperties properties) {
    boolean early =
        fightStartSeconds != null && fightStartSeconds < properties.getEarlyFightSeconds();
    Map<Boolean, List<FightReasonDomain>> byRanked =
        reasons.stream()
            .collect(Collectors.partitioningBy(r -> !(early && COOLDOWN.contains(r.getCode()))));
    List<FightReasonDomain> notRanked = byRanked.get(false);
    List<Entry> entries = collapseAbsence(byRanked.get(true));
    boolean playerLeads =
        entries.stream()
            .anyMatch(
                e ->
                    !isDraft(e)
                        && isPlayer(e.reason())
                        && severity(e.reason()) >= properties.getMinPlayerSeverity());
    Comparator<Entry> bySeverity = Comparator.comparing(Entry::reason, STRONGEST_FIRST);
    Comparator<Entry> byScope =
        playerLeads
            ? Comparator.comparing((Entry e) -> isPlayer(e.reason()) ? 0 : 1)
                .thenComparing(bySeverity)
            : bySeverity;
    Comparator<Entry> order =
        Comparator.comparing((Entry e) -> isDraft(e) ? 1 : 0).thenComparing(byScope);
    return new Ranking(entries.stream().sorted(order).toList(), notRanked);
  }

  /** How many of the reasons are about the team; the count {@code fight_explanation} stores. */
  public static int countOfTeam(List<FightReasonDomain> reasons, String team) {
    return (int) reasons.stream().filter(r -> team.equals(r.getTeam())).count();
  }

  private static List<Entry> collapseAbsence(List<FightReasonDomain> reasons) {
    Map<Long, List<FightReasonDomain>> absences =
        reasons.stream()
            .filter(ReasonRanking::isAbsence)
            .sorted(STRONGEST_FIRST)
            .collect(Collectors.groupingBy(FightReasonDomain::getSubjectSlot));
    List<Entry> entries = new ArrayList<>();
    for (FightReasonDomain r : reasons) {
      if (!isAbsence(r)) {
        entries.add(new Entry(r, List.of()));
      }
    }
    absences.values().forEach(family -> entries.add(entryOf(family)));
    return entries;
  }

  private static Entry entryOf(List<FightReasonDomain> strongestFirst) {
    return new Entry(
        strongestFirst.get(0),
        strongestFirst.stream().skip(1).map(FightReasonDomain::getCode).toList());
  }

  private static boolean isAbsence(FightReasonDomain reason) {
    return ABSENCE_FAMILY.contains(reason.getCode()) && reason.getSubjectSlot() != null;
  }

  private static boolean isDraft(Entry entry) {
    return DRAFT_LEVEL.contains(entry.reason().getCode());
  }

  private static boolean isPlayer(FightReasonDomain reason) {
    return ReasonScope.PLAYER.name().equals(reason.getScope());
  }

  private static double severity(FightReasonDomain reason) {
    return reason.getSeverity() == null ? 0 : reason.getSeverity();
  }
}
