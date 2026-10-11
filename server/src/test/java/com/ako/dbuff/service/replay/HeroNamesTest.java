package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeroNamesTest {

  @Test
  void matches_comparesCamelCaseEntityNamesWithOpenDotaNames() {
    assertThat(HeroNames.matches("SkeletonKing", "npc_dota_hero_skeleton_king")).isTrue();
    assertThat(HeroNames.matches("Antimage", "npc_dota_hero_antimage")).isTrue();
    assertThat(HeroNames.matches("Sniper", "npc_dota_hero_axe")).isFalse();
    assertThat(HeroNames.matches(null, "npc_dota_hero_axe")).isFalse();
  }
}
