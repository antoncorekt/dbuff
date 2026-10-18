package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;
import java.util.Map;

/**
 * What the aspects of an entry concluded about one hero, summed. The summary columns fit every
 * entry; what is particular to an aspect is in {@code figures} and the detail rows.
 *
 * @param itemKey the item the hero held (the first of a group)
 * @param kind the entry's main aspect, the first one it lists
 * @param purchaseSeconds when he first held it
 * @param count the uses (a farming group: creeps killed in the window after the purchase)
 * @param gain the gain in gold, the experience counted at the entry's weight
 * @param cost the gold the item cost; null when the constants do not say
 * @param ratio gain divided by cost; null when it cannot be told
 * @param paybackSeconds when the gain passed the cost; null when it never did
 * @param figures named totals of the aspects, for example hp, mana, saves, gold, xp
 * @param flags what the data could not tell, as codes the reports turn into words
 * @param rows one per use or per measured window
 */
public record ItemEfficiency(
    long slot,
    String entryKey,
    String itemKey,
    String kind,
    Double purchaseSeconds,
    int count,
    double gain,
    Long cost,
    Double ratio,
    Double paybackSeconds,
    Map<String, Double> figures,
    List<String> flags,
    List<Detail> rows) {

  /**
   * One use or one measured stretch.
   *
   * @param label the kind of row, an upper-case code
   * @param target what the use was on, for example the creep killed; null when not relevant
   */
  public record Detail(String label, double seconds, String target, Map<String, Double> figures) {}
}
