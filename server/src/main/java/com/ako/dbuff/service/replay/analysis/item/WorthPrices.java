package com.ako.dbuff.service.replay.analysis.item;

import java.util.Set;

/**
 * The prices and windows of the worth model. Defaults are first guesses, a YAML {@code prices}
 * entry replaces one for every item, and an item entry's {@code parameters} replace it for that
 * item.
 *
 * @param killWindowSeconds damage this long before an enemy's death counts toward that kill
 * @param xpWeight gold per point of experience
 * @param pokePrice gold per point of damage to an enemy hero who did not die
 * @param disableShare the share of a kill a disable that preceded it earns
 * @param trendMinutes the minutes before a purchase a trend baseline is fitted on
 * @param respawnSeconds respawn time assumed when the track cannot tell it
 */
record WorthPrices(
    double killWindowSeconds,
    double xpWeight,
    double pokePrice,
    double disableShare,
    double trendMinutes,
    double respawnSeconds) {

  static final Set<String> NAMES =
      Set.of(
          "killWindowSeconds",
          "xpWeight",
          "pokePrice",
          "disableShare",
          "trendMinutes",
          "respawnSeconds");

  static WorthPrices of(AspectContext context) {
    return new WorthPrices(
        context.price("killWindowSeconds", 10),
        context.price("xpWeight", 1),
        context.price("pokePrice", 0.02),
        context.price("disableShare", 0.25),
        context.price("trendMinutes", 8),
        context.price("respawnSeconds", 30));
  }
}
