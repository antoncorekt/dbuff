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

  /** The most an item of the entry costs among those this hero held; the first item without one. */
  public long cost(List<String> held) {
    return held.stream()
        .mapToLong(name -> kits.cost(HeroInputs.itemKey(name)))
        .max()
        .orElseGet(() -> kits.cost(entry.items().getFirst()));
  }
}
