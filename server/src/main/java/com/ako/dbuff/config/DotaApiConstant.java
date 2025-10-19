package com.ako.dbuff.config;

import lombok.Getter;

@Getter
public enum DotaApiConstant {
  GAME_MODE("game_mode"),
  HERO_ABILITIES("hero_abilities"),
  HERO("heroes"),
  ABILITIES_IDS("ability_ids"),
  ABILITIES("abilities"),
  ITEMS("items"),
  PATCH("patch"),
  ;

  private final String name;

  DotaApiConstant(String name) {
    this.name = name;
  }
}
