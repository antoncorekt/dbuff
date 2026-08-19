package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.CacheConfig;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.constant.data.PatchConstant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConstantsManagers {

  private final ItemConstantService itemConstantService;
  private final HeroConstantService heroConstantService;
  private final AbilityIdsConstantService abilityIdsConstantService;
  private final HeroAbilitiesConstantService heroAbilitiesConstantService;
  private final AbilitiesConstantService abilitiesConstantService;
  private final MatchTypeConstantService matchTypeConstantService;
  private final PatchConstantService patchConstantService;

  @Cacheable(CacheConfig.ITEM_CONSTANT_CACHE)
  public Map<String, ItemConstant> getItemConstantMap() {
    return itemConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.ITEM_CONSTANT_CACHE)
  public Map<Long, ItemConstant> getItemByIdConstantMap() {
    return itemConstantService.getConstantMap().entrySet().stream()
        .collect(Collectors.toMap(x -> x.getValue().getId(), x -> x.getValue()));
  }

  @Cacheable(CacheConfig.HERO_CONSTANT_CACHE)
  public Map<String, HeroConstant> getHeroConstantMap() {
    return heroConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.ABILITY_ID_CONSTANT_CACHE)
  public Map<String, AbilityIdsConstant> getAbilityConstantMap() {
    return abilityIdsConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.ALL_HERO_ABILITIES_CACHE)
  public Set<String> getAllHeroAbilities() {
    return heroAbilitiesConstantService.getConstantMap().values().stream()
        .flatMap(x -> x.getAbilities().stream())
        .collect(Collectors.toSet());
  }

  /**
   * Hero abilities keyed by internal hero name, e.g. {@code npc_dota_hero_invoker}.
   *
   * <p>{@link #getAllHeroAbilities()} flattens every hero into one set, which loses the association
   * needed to narrow ability autocomplete to a chosen hero.
   *
   * @return internal hero name to that hero's abilities
   */
  @Cacheable(CacheConfig.HERO_ABILITIES_BY_HERO_CACHE)
  public Map<String, HeroesAbilityConstant> getHeroAbilitiesMap() {
    return heroAbilitiesConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.ALL_ABILITIES_CACHE)
  public Map<String, AbilityConstant> getAllAbilityConstants() {
    return abilitiesConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.MATCH_TYPE_CACHE)
  public Map<String, MatchTypeConstant> getMatchTypeConstantMap() {
    return matchTypeConstantService.getConstantMap();
  }

  @Cacheable(CacheConfig.MATCH_TYPE_BY_NAME_CACHE)
  public Map<String, MatchTypeConstant> getMatchTypeByNameConstantMap() {
    return matchTypeConstantService.getConstantMap().values().stream()
        .collect(
            Collectors.toMap(x -> x.getName().replace("game_mode_", "").replace("_", " "), x -> x));
  }

  @Cacheable(CacheConfig.PATCH_CACHE)
  public Map<String, PatchConstant> getPatchConstantMap() {
    return patchConstantService.getConstantMap();
  }
}
