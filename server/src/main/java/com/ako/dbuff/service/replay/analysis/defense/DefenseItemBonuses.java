package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Armor and magic resistance an item gives its holder, read from the constants' attribute display
 * texts because the attribute keys differ between items and auras have no display.
 */
@Component
@RequiredArgsConstructor
public class DefenseItemBonuses {
  public record Bonus(double armor, double magicResist) {
    public static final Bonus NONE = new Bonus(0, 0);
  }

  private static final String NUMBER = "(\\d+(?:\\.\\d+)?)";
  private static final Pattern PLUS_ARMOR = Pattern.compile("\\+ \\{value} Armor");
  private static final Pattern MINUS_ARMOR = Pattern.compile("- \\{value} Armor");
  private static final Pattern MAGIC_RESIST = Pattern.compile("\\+ \\{value}% Magic Resistance");
  private static final Pattern PLAIN_NUMBER = Pattern.compile(NUMBER);

  private final ItemKits kits;
  private final Map<String, Bonus> cache = new ConcurrentHashMap<>();

  public Bonus of(String itemName) {
    String key = itemName.startsWith("item_") ? itemName.substring("item_".length()) : itemName;
    return cache.computeIfAbsent(key, this::compute);
  }

  private Bonus compute(String key) {
    List<Map<String, String>> attrib =
        kits.item(key).map(ItemConstant::getAttrib).orElse(List.of());
    double armor = 0;
    double keptMagic = 1;
    for (Map<String, String> entry : attrib) {
      String display = entry.get("display");
      String value = entry.get("value");
      if (display == null || value == null) {
        continue;
      }
      Matcher number = PLAIN_NUMBER.matcher(value.trim());
      if (!number.matches()) {
        continue;
      }
      double amount = Double.parseDouble(number.group(1));
      if (PLUS_ARMOR.matcher(display).matches()) {
        armor += amount;
      } else if (MINUS_ARMOR.matcher(display).matches()) {
        armor -= amount;
      } else if (MAGIC_RESIST.matcher(display).matches()) {
        keptMagic *= 1 - amount / 100;
      }
    }
    return new Bonus(armor, 1 - keptMagic);
  }
}
