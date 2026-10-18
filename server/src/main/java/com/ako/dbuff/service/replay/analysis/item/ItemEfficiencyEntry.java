package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;
import java.util.Map;

/**
 * One entry of {@code replay/items.yaml}: an item or a group of items, the aspects that judge it
 * and their parameters (first guesses, tuned in the review loop).
 *
 * @param key the item key or the group name; stored with the rows
 * @param category the catalog category the entry belongs to
 * @param items the item keys of OpenDota's constants (no {@code item_} prefix) the entry covers
 * @param aspects the {@link ItemAspect} names that judge the entry; the first is the main one
 * @param winStats the item combinations whose win rates are shown; empty when none
 * @param alternatives the candidate items to compare with; empty when the data picks them
 * @param parameters named numbers for the aspects; one they do not list falls back to its default
 */
public record ItemEfficiencyEntry(
    String key,
    String category,
    List<String> items,
    List<String> aspects,
    List<List<String>> winStats,
    List<String> alternatives,
    Map<String, Double> parameters) {

  public double parameter(String name, double fallback) {
    return parameters.getOrDefault(name, fallback);
  }
}
