package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Looks up what OpenDota's constants say about an item, by its key without the item_ prefix. */
public interface ItemKits {
  Optional<ItemConstant> item(String key);

  /** One number of the item's attributes, for example bonus_damage; empty when it has none. */
  default OptionalDouble attribute(String key, String attribute) {
    return item(key).map(ItemConstant::getAttrib).stream()
        .flatMap(java.util.List::stream)
        .filter(a -> attribute.equals(a.get("key")))
        .map(a -> a.get("value"))
        .filter(v -> v != null && v.matches("\\d+(\\.\\d+)?"))
        .mapToDouble(Double::parseDouble)
        .findFirst();
  }

  /** The gold cost of the item; empty when the constants lack the item or give it no cost. */
  default OptionalLong cost(String key) {
    return item(key).map(ItemConstant::getCost).map(OptionalLong::of).orElse(OptionalLong.empty());
  }
}
