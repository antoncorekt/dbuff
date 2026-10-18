package com.ako.dbuff.service.replay.analysis.item;

import java.util.Set;

/**
 * One judgment of an item, measured for one hero: what the item gave in gold and the evidence for
 * it. An entry of {@code replay/items.yaml} lists the aspects that judge it, the analyzer sums
 * their parts. A new kind of judgment is one new bean; a new item of a known kind is a new entry.
 */
public interface ItemAspect {
  /** The name the entries list under {@code aspects}. */
  String name();

  /** The parameter names the aspect reads, so a misspelt one in the file is caught. */
  Set<String> parameterNames();

  AspectPart measure(AspectContext context);
}
