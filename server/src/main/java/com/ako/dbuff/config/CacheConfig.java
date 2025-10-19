package com.ako.dbuff.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  public static final String ITEM_CONSTANT_CACHE = "item_constant";
  public static final String ITEM_CONSTANT_BY_ID_CACHE = "item_constant_by_it";
  public static final String HERO_CONSTANT_CACHE = "hero_constant";
  public static final String ABILITY_ID_CONSTANT_CACHE = "ability_it_constant";
  public static final String ALL_HERO_ABILITIES_CACHE = "all_hero_abilities";
  public static final String ALL_ABILITIES_CACHE = "all_abilities";
  public static final String MATCH_TYPE_CACHE = "match_type";
  public static final String PATCH_CACHE = "patch_cache";

  @Bean
  public CacheManager cacheManager() {
    CacheManager cacheManager =
        new ConcurrentMapCacheManager(
            ITEM_CONSTANT_CACHE,
            ITEM_CONSTANT_BY_ID_CACHE,
            HERO_CONSTANT_CACHE,
            ABILITY_ID_CONSTANT_CACHE,
            ALL_HERO_ABILITIES_CACHE,
            ALL_ABILITIES_CACHE,
            MATCH_TYPE_CACHE,
            PATCH_CACHE);
    return cacheManager;
  }
}
