package com.ako.dbuff.service.replay.analysis.item;

/**
 * What a death costs a hero, over the {@code health} rule of the same name, which the simple name
 * cannot import here.
 */
final class DeathCost {
  private final com.ako.dbuff.service.replay.analysis.health.DeathCost rule;

  /** The cost of a death; see the {@code health} rule for the figures. */
  static final class Cost {
    private final com.ako.dbuff.service.replay.analysis.health.DeathCost.Cost cost;

    private Cost(com.ako.dbuff.service.replay.analysis.health.DeathCost.Cost cost) {
      this.cost = cost;
    }

    double goldLost() {
      return cost.goldLost();
    }

    double respawnSeconds() {
      return cost.respawnSeconds();
    }

    double goldPerMinute() {
      return cost.goldPerMinute();
    }

    boolean estimated() {
      return cost.estimated();
    }

    double value() {
      return cost.value();
    }
  }

  DeathCost(HeroInputs hero, WorthPrices prices) {
    this.rule =
        new com.ako.dbuff.service.replay.analysis.health.DeathCost(
            hero.hero(),
            hero.events(),
            hero.track(),
            MinuteSeries.during(hero.match().minutesOf(hero.slot()), MinuteSeries::lostToDeath),
            MinuteSeries.during(hero.match().minutesOf(hero.slot()), MinuteSeries::earned),
            prices.respawnSeconds());
  }

  Cost at(double second) {
    return new Cost(rule.at(second));
  }

  Cost avoided(double second) {
    return new Cost(rule.avoided(second));
  }
}
