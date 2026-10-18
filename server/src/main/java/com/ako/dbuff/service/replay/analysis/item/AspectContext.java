package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;
import java.util.Map;

/**
 * What an aspect reads: the entry, the constants and one hero's stored facts of the match, who held
 * at least one of the entry's items.
 *
 * @param gameEndSeconds the last second the track or the combat log reaches
 * @param names the replay names of the entry's items, as the state changes and the combat log write
 *     them
 * @param prices the catalog's shared prices; an entry's parameter of the same name replaces one
 */
public record AspectContext(
    ItemEfficiencyEntry entry,
    ItemKits kits,
    int gameEndSeconds,
    HeroInputs hero,
    List<String> names,
    Map<String, Double> prices) {

  public static AspectContext of(
      ItemEfficiencyEntry entry,
      ItemKits kits,
      int gameEndSeconds,
      HeroInputs hero,
      Map<String, Double> prices) {
    return new AspectContext(
        entry,
        kits,
        gameEndSeconds,
        hero,
        entry.items().stream().map(HeroInputs::replayName).toList(),
        prices);
  }

  /** The entry's parameter, else the catalog price, else the fallback. */
  public double price(String name, double fallback) {
    return entry.parameter(name, prices.getOrDefault(name, fallback));
  }

  /** The entry's items the hero held, in the order he got them. */
  public List<String> held() {
    return hero.held(names);
  }

  /**
   * The most an item among those the hero held costs, the final item of an upgrade chain; null when
   * the constants know none of them.
   */
  public Long cost() {
    return held().stream()
        .map(name -> kits.cost(HeroInputs.itemKey(name)))
        .filter(java.util.OptionalLong::isPresent)
        .map(java.util.OptionalLong::getAsLong)
        .max(Long::compare)
        .orElse(null);
  }

  /** When the hero first got the first item he held. */
  public double bought() {
    return hero.firstAdded(List.of(held().getFirst())).orElse(0);
  }
}
