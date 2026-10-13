package com.ako.dbuff.service.replay.analysis.item;

import java.util.List;
import java.util.Set;

/**
 * Judges the entries of one kind. Adding an item of a known kind is a new entry in {@code
 * replay/item-efficiency.json}; an item whose efficiency means something else is one new
 * implementation, discovered as a bean, with no change to the analyzer.
 */
public interface ItemEfficiencyEvaluator {
  boolean supports(ItemEfficiencyEntry entry);

  /** The parameter names the evaluator reads, so a misspelt one in the file is caught. */
  Set<String> parameterNames();

  /** One row per hero who owned one of the entry's items; none when nobody did. */
  List<ItemEfficiency> evaluate(EntryContext context);
}
