package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Optional;

/** Looks up what OpenDota's constants say about an item, by its key without the item_ prefix. */
public interface ItemKits {
  Optional<ItemConstant> item(String key);

  /** The gold cost of the item; the file's items must all be in the constants. */
  default long cost(String key) {
    return item(key)
        .map(ItemConstant::getCost)
        .orElseThrow(() -> new IllegalStateException("The item constants have no cost for " + key));
  }
}
