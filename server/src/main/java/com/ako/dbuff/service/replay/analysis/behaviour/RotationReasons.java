package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Lists every reason that applies to a trip, in the order of {@link RotationReason}. */
public final class RotationReasons {
  private record Rule(RotationReason reason, Predicate<ReasonFacts> applies) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule(RotationReason.DIED, f -> f.trip().end() == AwayTrip.End.DIED),
          new Rule(
              RotationReason.DID_NOTHING,
              f ->
                  f.outcome().fights() == 0
                      && f.heroHits() == 0
                      && f.towerHits() == 0
                      && f.outcome().kills() == 0
                      && f.outcome().creepKills() == 0),
          new Rule(
              RotationReason.INCOME_DROP,
              f -> f.income().map(i -> i.dropped(f.incomeShare())).orElse(false)),
          new Rule(RotationReason.ARRIVED_AFTER_FIGHT_ENDED, ReasonFacts::missedFight),
          new Rule(
              RotationReason.LEFT_LANE_UNDEFENDED,
              f -> f.proxy().map(LaneProxy.Result::lost).orElse(false)),
          new Rule(RotationReason.TOWER_LOST_WHILE_AWAY, ReasonFacts::towerLost));

  static {
    List<RotationReason> ruled = RULES.stream().map(Rule::reason).toList();
    if (!ruled.equals(Arrays.asList(RotationReason.values()))) {
      throw new IllegalStateException("Every rotation reason needs one rule, in order");
    }
  }

  private RotationReasons() {}

  public static List<RotationReason> of(ReasonFacts facts) {
    return RULES.stream().filter(r -> r.applies().test(facts)).map(Rule::reason).toList();
  }
}
