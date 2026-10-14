package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The ability's display name from the constants. Without one, the prettified internal name minus a
 * leading hero token, since an Ability Draft hero holds abilities of other heroes and the source
 * hero's name would read as part of the ability.
 */
@Component
@RequiredArgsConstructor
public class ConstantAbilityLabels implements AbilityLabels {
  private static final String HERO_PREFIX = "npc_dota_hero_";
  private static final String ITEM_PREFIX = "item_";

  private final ConstantsManagers constants;

  @Override
  public String label(String internalName) {
    AbilityConstant ability = constants.getAllAbilityConstants().get(internalName);
    if (ability != null && ability.getDname() != null && !ability.getDname().isBlank()) {
      return ability.getDname();
    }
    return Prettify.ability(withoutHeroToken(internalName));
  }

  private String withoutHeroToken(String internalName) {
    if (internalName.startsWith(ITEM_PREFIX)) {
      return internalName;
    }
    for (Map.Entry<String, HeroConstant> hero : constants.getHeroConstantMap().entrySet()) {
      String name = hero.getValue().getName();
      if (name == null || !name.startsWith(HERO_PREFIX)) {
        continue;
      }
      String prefix = name.substring(HERO_PREFIX.length()) + "_";
      if (internalName.startsWith(prefix) && internalName.length() > prefix.length()) {
        return internalName.substring(prefix.length());
      }
    }
    return internalName;
  }
}
