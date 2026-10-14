package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The icons to sync, from the constants. Talents and entries without an image have no icon; recipes
 * are not items one holds. Abilities are limited to those seen in stored matches unless all are
 * asked for.
 */
@Component
@RequiredArgsConstructor
public class IconCandidates {
  public static final String HERO_PREFIX = "npc_dota_hero_";
  static final String ITEM_PREFIX = "item_";
  private static final String TALENT_PREFIX = "special_bonus_";
  private static final String RECIPE_PREFIX = "recipe_";

  private final ConstantsManagers constants;
  private final AbilityDraftPickRepo draftPickRepo;
  private final FightAbilityUseRepo abilityUseRepo;

  public List<IconCandidate> of(IconKind kind, boolean allAbilities) {
    return switch (kind) {
      case HERO -> heroes();
      case ITEM -> items();
      case ABILITY -> abilities(allAbilities);
    };
  }

  public static String heroKey(HeroConstant hero) {
    String name = hero.getName();
    return name != null && name.startsWith(HERO_PREFIX)
        ? name.substring(HERO_PREFIX.length())
        : null;
  }

  private List<IconCandidate> heroes() {
    return constants.getHeroConstantMap().values().stream()
        .filter(h -> heroKey(h) != null && usable(h.getImg()))
        .map(h -> new IconCandidate(IconKind.HERO, heroKey(h), h.getImg()))
        .sorted(byKey())
        .toList();
  }

  private List<IconCandidate> items() {
    return constants.getItemConstantMap().entrySet().stream()
        .filter(e -> !e.getKey().startsWith(RECIPE_PREFIX) && usable(e.getValue().getImg()))
        .map(e -> new IconCandidate(IconKind.ITEM, e.getKey(), e.getValue().getImg()))
        .sorted(byKey())
        .toList();
  }

  private List<IconCandidate> abilities(boolean all) {
    Set<String> seen = all ? null : seenAbilityNames();
    Map<String, AbilityConstant> abilities = constants.getAllAbilityConstants();
    return abilities.entrySet().stream()
        .filter(e -> !e.getKey().startsWith(TALENT_PREFIX))
        .filter(e -> usable(e.getValue().getImg()) && usable(e.getValue().getDname()))
        .filter(e -> seen == null || seen.contains(e.getKey()))
        .map(e -> new IconCandidate(IconKind.ABILITY, e.getKey(), e.getValue().getImg()))
        .sorted(byKey())
        .toList();
  }

  private Set<String> seenAbilityNames() {
    Set<String> names = new HashSet<>(draftPickRepo.findDistinctAbilityNames());
    names.addAll(abilityUseRepo.findDistinctAbilityNames());
    names.removeIf(n -> n.startsWith(ITEM_PREFIX));
    return names;
  }

  private static boolean usable(String text) {
    return text != null && !text.isBlank();
  }

  private static Comparator<IconCandidate> byKey() {
    return Comparator.comparing(IconCandidate::key);
  }
}
