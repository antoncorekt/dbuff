package com.ako.dbuff.service.replay.analysis.skill;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One kind of judgment on the casts of a catalogued skill. Aspects know no skill by name: an entry
 * of {@code replay/skills.yaml} lists the aspects that judge it.
 */
public interface SkillAspect {

  /**
   * One judged cast, or a chance the hero missed.
   *
   * @param second game second of the cast, or of the event the chance was missed at
   * @param target the unit the cast was aimed at or the ally who died; empty when none
   * @param outcome one of {@link #outcomes()}
   * @param cast true for a cast of the hero, false for a missed chance (no cast happened)
   * @param numbers the figures behind the outcome
   */
  record Outcome(
      double second, String target, String outcome, boolean cast, Map<String, Double> numbers) {}

  String name();

  /** The parameter names the aspect reads; the file may set only these for its entries. */
  Set<String> parameterNames();

  /** Every outcome the aspect can give, in report order. */
  List<String> outcomes();

  /** True when the aspect reads the cast snapshots, so its abilities must be recorded. */
  default boolean needsSnapshots() {
    return false;
  }

  /** Extra figures derived from the outcome counts of one hero and entry, e.g. an accuracy. */
  default Map<String, Integer> derivedFigures(Map<String, Integer> counts) {
    return Map.of();
  }

  List<Outcome> judge(SkillContext context);
}
