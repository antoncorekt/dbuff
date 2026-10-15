package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.icon.IconService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The display name of an ability or item from the constants. Without one, the prettified internal
 * name minus a leading hero token, since an Ability Draft hero holds abilities of other heroes and
 * the source hero's name would read as part of the ability. It never fails: missing constants give
 * the prettified name. The icon of the ability or item goes before the name, never into the cache,
 * since the synced emoji change.
 */
@Component
@RequiredArgsConstructor
public class ConstantAbilityLabels implements AbilityLabels {
  private static final String HERO_PREFIX = "npc_dota_hero_";
  private static final String ITEM_PREFIX = "item_";

  private final ConstantsManagers constants;
  private final IconService icons;
  private volatile Snapshot snapshot;

  @Override
  public String label(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return "";
    }
    Snapshot current = current();
    return icons.ability(internalName)
        + current.labels().computeIfAbsent(internalName, current::compute);
  }

  private Snapshot current() {
    Map<String, AbilityConstant> abilities = safe(constants::getAllAbilityConstants);
    Map<String, ItemConstant> items = safe(constants::getItemConstantMap);
    Map<String, HeroConstant> heroes = safe(constants::getHeroConstantMap);
    Snapshot known = snapshot;
    if (known != null && known.isBuiltFrom(abilities, items, heroes)) {
      return known;
    }
    Snapshot fresh = new Snapshot(abilities, items, heroes);
    snapshot = fresh;
    return fresh;
  }

  private static <V> Map<String, V> safe(java.util.function.Supplier<Map<String, V>> source) {
    try {
      Map<String, V> map = source.get();
      return map == null ? Map.of() : map;
    } catch (RuntimeException e) {
      return Map.of();
    }
  }

  private record Snapshot(
      Map<String, AbilityConstant> abilities,
      Map<String, ItemConstant> items,
      Map<String, HeroConstant> heroes,
      List<String> heroPrefixes,
      Map<String, String> labels) {

    Snapshot(
        Map<String, AbilityConstant> abilities,
        Map<String, ItemConstant> items,
        Map<String, HeroConstant> heroes) {
      this(abilities, items, heroes, prefixes(heroes), new ConcurrentHashMap<>());
    }

    boolean isBuiltFrom(
        Map<String, AbilityConstant> otherAbilities,
        Map<String, ItemConstant> otherItems,
        Map<String, HeroConstant> otherHeroes) {
      return abilities == otherAbilities && items == otherItems && heroes == otherHeroes;
    }

    String compute(String internalName) {
      if (internalName.startsWith(ITEM_PREFIX)) {
        String dname = itemDname(internalName.substring(ITEM_PREFIX.length()));
        return dname != null ? dname : Prettify.ability(internalName);
      }
      AbilityConstant ability = abilities.get(internalName);
      if (ability != null && usable(ability.getDname())) {
        return ability.getDname();
      }
      return Prettify.ability(withoutHeroToken(internalName));
    }

    private String itemDname(String key) {
      ItemConstant item = items.get(key);
      return item != null && usable(item.getDname()) ? item.getDname() : null;
    }

    private String withoutHeroToken(String internalName) {
      return heroPrefixes.stream()
          .filter(
              prefix -> internalName.startsWith(prefix) && internalName.length() > prefix.length())
          .findFirst()
          .map(prefix -> internalName.substring(prefix.length()))
          .orElse(internalName);
    }

    private static List<String> prefixes(Map<String, HeroConstant> heroes) {
      return heroes.values().stream()
          .filter(Objects::nonNull)
          .map(HeroConstant::getName)
          .filter(name -> name != null && name.startsWith(HERO_PREFIX))
          .map(name -> name.substring(HERO_PREFIX.length()) + "_")
          .distinct()
          .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s))
          .toList();
    }

    private static boolean usable(String name) {
      return name != null && !name.isBlank();
    }
  }
}
