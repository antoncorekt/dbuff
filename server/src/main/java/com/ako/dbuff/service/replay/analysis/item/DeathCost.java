package com.ako.dbuff.service.replay.analysis.item;

final class DeathCost extends com.ako.dbuff.service.replay.analysis.health.DeathCost {
  DeathCost(HeroInputs hero, WorthPrices prices) {
    super(
        hero.hero(),
        hero.events(),
        hero.track(),
        MinuteSeries.during(hero.match().minutesOf(hero.slot()), MinuteSeries::lostToDeath),
        MinuteSeries.during(hero.match().minutesOf(hero.slot()), MinuteSeries::earned),
        prices.respawnSeconds());
  }
}
