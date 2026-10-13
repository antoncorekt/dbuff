package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;

/**
 * What an evaluator reads: the entry, the constants and every hero's stored facts of the match.
 *
 * @param gameEndSeconds the last second the track or the combat log reaches
 */
public record EntryContext(
    ItemEfficiencyEntry entry, ItemKits kits, int gameEndSeconds, List<HeroInputs> heroes) {

  /** The replay names of the entry's items, as the state changes and the combat log write them. */
  public List<String> replayNames() {
    return entry.items().stream().map(HeroInputs::replayName).toList();
  }

  /**
   * The most an item among those the hero held costs, the final item of an upgrade chain; null when
   * the constants know none of them.
   */
  public Long cost(List<String> held) {
    return held.stream()
        .map(name -> kits.cost(HeroInputs.itemKey(name)))
        .filter(java.util.OptionalLong::isPresent)
        .map(java.util.OptionalLong::getAsLong)
        .max(Long::compare)
        .orElse(null);
  }
}
