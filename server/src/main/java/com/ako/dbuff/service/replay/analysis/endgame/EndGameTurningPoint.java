package com.ako.dbuff.service.replay.analysis.endgame;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The fight the game turned on: the winner's first won fight after the loser's peak lead that was
 * followed by an objective, or else the winner's won fight after it that moved the gold most.
 */
public final class EndGameTurningPoint {
  public static final String CONVERTED = "CONVERTED";
  public static final String SWING = "SWING";

  private EndGameTurningPoint() {}

  /**
   * @param conversion the objective row of the fight when the kind is CONVERTED, else null
   * @param importance the gold change of the fight; null without an importance row
   */
  public record Pick(
      String kind, FightDomain fight, ObjectiveConversionDomain conversion, Long goldChange) {}

  private static final Comparator<FightDomain> BY_TIME =
      Comparator.comparing(FightDomain::getStartSeconds)
          .thenComparing(
              FightDomain::getFightIndex, Comparator.nullsLast(Comparator.naturalOrder()));

  public static Optional<Pick> pick(
      String winner,
      double peakSeconds,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance,
      List<ObjectiveConversionDomain> conversions) {
    Map<Integer, FightDomain> after =
        fights.stream()
            .filter(f -> f.getFightIndex() != null && f.getStartSeconds() != null)
            .filter(f -> f.getStartSeconds() >= peakSeconds)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    Map<Integer, FightImportanceDomain> gold =
        importance.stream()
            .filter(i -> i.getFightIndex() != null && winner.equals(i.getWinningTeam()))
            .collect(Collectors.toMap(FightImportanceDomain::getFightIndex, i -> i, (a, b) -> a));
    Optional<Pick> converted =
        conversions.stream()
            .filter(Objects::nonNull)
            .filter(c -> winner.equals(c.getWinnerTeam()) && CONVERTED.equals(c.getResult()))
            .filter(c -> after.containsKey(c.getFightIndex()))
            .min(Comparator.comparing(c -> after.get(c.getFightIndex()), BY_TIME))
            .map(
                c ->
                    new Pick(
                        CONVERTED,
                        after.get(c.getFightIndex()),
                        c,
                        goldChange(gold.get(c.getFightIndex()))));
    if (converted.isPresent()) {
      return converted;
    }
    return gold.values().stream()
        .filter(i -> i.getGoldChange() != null && after.containsKey(i.getFightIndex()))
        .max(
            Comparator.comparing(FightImportanceDomain::getGoldChange)
                .thenComparing(i -> -after.get(i.getFightIndex()).getStartSeconds()))
        .map(i -> new Pick(SWING, after.get(i.getFightIndex()), null, i.getGoldChange()));
  }

  private static Long goldChange(FightImportanceDomain importance) {
    return importance == null ? null : importance.getGoldChange();
  }
}
