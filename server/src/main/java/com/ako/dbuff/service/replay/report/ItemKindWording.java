package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;

/**
 * How the rows of {@code item_efficiency} with one main aspect (the first aspect of their entry)
 * are worded. A new main aspect is one more bean; a row without one is not reported.
 */
public interface ItemKindWording {

  /** The main aspect of the rows this wording reads. */
  String aspect();

  /** Whether the row has every figure the wording prints, so it never says "?". */
  boolean complete(ItemEfficiency row);

  /** The row in full, with the limits of the data in brackets: the item loop report's phrase. */
  String longVerdict(ItemEfficiency row);

  /** The row in one short sentence that starts with the subject, for the Discord section. */
  String shortVerdict(ItemEfficiency row, String subject);
}
