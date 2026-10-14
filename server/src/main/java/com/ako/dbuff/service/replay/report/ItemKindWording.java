package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;

/**
 * How one evaluator kind of {@code item_efficiency} is worded. A new kind is one more bean; a kind
 * without one is not reported.
 */
public interface ItemKindWording {

  /** The evaluator kind of the rows this wording reads. */
  String kind();

  /** Whether the row has every figure the wording prints, so it never says "?". */
  boolean complete(ItemEfficiency row);

  /** The row in full, with the limits of the data in brackets: the item loop report's phrase. */
  String longVerdict(ItemEfficiency row);

  /** The row in one short sentence that starts with the subject, for the Discord section. */
  String shortVerdict(ItemEfficiency row, String subject);
}
