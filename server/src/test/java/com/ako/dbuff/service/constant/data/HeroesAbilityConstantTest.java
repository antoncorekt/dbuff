package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HeroesAbilityConstantTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserialize_allStringAbilities_parsedCorrectly() throws Exception {
    String json =
        """
        {
          "npc_dota_hero_antimage": {
            "abilities": [
              "antimage_mana_break",
              "antimage_blink",
              "antimage_counterspell",
              "generic_hidden",
              "antimage_persectur",
              "antimage_mana_void"
            ]
          }
        }
        """;

    Map<String, HeroesAbilityConstant> result =
        objectMapper.readValue(json, new TypeReference<>() {});

    HeroesAbilityConstant am = result.get("npc_dota_hero_antimage");
    assertNotNull(am);
    assertEquals(
        List.of(
            "antimage_mana_break",
            "antimage_blink",
            "antimage_counterspell",
            "generic_hidden",
            "antimage_persectur",
            "antimage_mana_void"),
        am.getAbilities());
  }

  @Test
  void deserialize_nestedArrayAbilities_flattenedCorrectly() throws Exception {
    // Monkey King has a nested array at index 7:
    // ["monkey_king_untransform", "monkey_king_transfiguration"]
    String json =
        """
        {
          "npc_dota_hero_monkey_king": {
            "abilities": [
              "monkey_king_boundless_strike",
              "monkey_king_tree_dance",
              "monkey_king_primal_spring",
              "monkey_king_jingu_mastery",
              "monkey_king_mischief",
              "monkey_king_wukongs_command",
              "monkey_king_primal_spring_early",
              ["monkey_king_untransform", "monkey_king_transfiguration"]
            ]
          }
        }
        """;

    Map<String, HeroesAbilityConstant> result =
        objectMapper.readValue(json, new TypeReference<>() {});

    HeroesAbilityConstant mk = result.get("npc_dota_hero_monkey_king");
    assertNotNull(mk);
    assertEquals(
        List.of(
            "monkey_king_boundless_strike",
            "monkey_king_tree_dance",
            "monkey_king_primal_spring",
            "monkey_king_jingu_mastery",
            "monkey_king_mischief",
            "monkey_king_wukongs_command",
            "monkey_king_primal_spring_early",
            "monkey_king_untransform",
            "monkey_king_transfiguration"),
        mk.getAbilities());
  }

  @Test
  void deserialize_mixedHeroes_allParsedCorrectly() throws Exception {
    // Both a normal hero and Monkey King in the same payload
    String json =
        """
        {
          "npc_dota_hero_axe": {
            "abilities": [
              "axe_berserkers_call",
              "axe_battle_hunger",
              "axe_counter_helix"
            ]
          },
          "npc_dota_hero_monkey_king": {
            "abilities": [
              "monkey_king_boundless_strike",
              ["monkey_king_untransform", "monkey_king_transfiguration"]
            ]
          }
        }
        """;

    Map<String, HeroesAbilityConstant> result =
        objectMapper.readValue(json, new TypeReference<>() {});

    assertEquals(2, result.size());

    assertEquals(
        List.of("axe_berserkers_call", "axe_battle_hunger", "axe_counter_helix"),
        result.get("npc_dota_hero_axe").getAbilities());

    assertEquals(
        List.of(
            "monkey_king_boundless_strike",
            "monkey_king_untransform",
            "monkey_king_transfiguration"),
        result.get("npc_dota_hero_monkey_king").getAbilities());
  }
}
