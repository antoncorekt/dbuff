package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;
import java.util.Map;

/**
 * One line of {@code replay/item-efficiency.json}: an item or a group of items, the kind of
 * evaluator that judges it, and that evaluator's parameters (first guesses, tuned in the review
 * loop).
 *
 * @param key the item key or the group name; stored with the rows
 * @param kind which {@link ItemEfficiencyEvaluator} judges the entry
 * @param items the item keys of OpenDota's constants (no {@code item_} prefix) the entry covers
 * @param parameters named numbers for the evaluator; one it does not list falls back to its default
 */
public record ItemEfficiencyEntry(
    String key, String kind, List<String> items, Map<String, Double> parameters) {

  public double parameter(String name, double fallback) {
    return parameters.getOrDefault(name, fallback);
  }

  public int parameter(String name, int fallback) {
    return (int) Math.round(parameter(name, (double) fallback));
  }
}
