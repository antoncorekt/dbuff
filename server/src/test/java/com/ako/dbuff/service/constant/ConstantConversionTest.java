package com.ako.dbuff.service.constant;

import com.ako.dbuff.dao.model.converter.ConstantConverter;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ConstantConversionTest {

  private final ObjectMapper reader = new ObjectMapper();
  private final HeroConstantService heroService =
      new HeroConstantService(mock(ConstantDomainRepo.class), mock(ConstantFetcherService.class));
  private final AbilitiesConstantService abilityService =
      new AbilitiesConstantService(
          mock(ConstantDomainRepo.class), mock(ConstantFetcherService.class));

  @Test
  void hero_keepsRoleModelFields() throws Exception {
    HeroConstant am = heroService.convert(load("constants_heroes.json")).get("1");

    assertEquals("1", am.getId());
    assertEquals("npc_dota_hero_antimage", am.getName());
    assertEquals("Anti-Mage", am.getLocalized_name());
    assertEquals("agi", am.getPrimary_attr());
    assertEquals("Melee", am.getAttack_type());
    assertEquals(List.of("Carry", "Escape", "Nuker"), am.getRoles());
    assertEquals(120.0, am.getBase_health());
    assertEquals(25.0, am.getBase_mr());
    assertEquals(2.8, am.getAgi_gain());
    assertEquals(150.0, am.getAttack_range());
    assertEquals(1.4, am.getAttack_rate());
    assertEquals(310.0, am.getMove_speed());
  }

  @Test
  void hero_rangedHeroIsParsed() throws Exception {
    HeroConstant cm = heroService.convert(load("constants_heroes.json")).get("5");

    assertEquals("Ranged", cm.getAttack_type());
    assertEquals("int", cm.getPrimary_attr());
    assertTrue(cm.getRoles().contains("Support"));
  }

  @Test
  void ability_listBehaviourAndPerLevelValues() throws Exception {
    AbilityConstant ult =
        abilityService.convert(load("constants_abilities.json")).get("antimage_mana_void");

    assertEquals("Mana Void", ult.getDname());
    assertEquals(List.of("Unit Target", "AOE"), ult.getBehavior());
    assertEquals(List.of("Magical"), ult.getDmg_type());
    assertEquals(List.of("No"), ult.getBkbpierce());
    assertEquals(List.of("Enemy"), ult.getTarget_team());
    assertEquals(List.of("Hero", "Basic"), ult.getTarget_type());
    assertEquals(List.of(100.0, 85.0, 70.0), ult.getCd());
    assertEquals(List.of(100.0, 150.0, 200.0), ult.getMc());
    assertEquals("mana_void_damage_per_mana", ult.getAttrib().get(0).getKey());
    assertEquals(List.of("1"), ult.getAttrib().get(0).getValue());
  }

  @Test
  void ability_singleValuesBecomeOneElementLists() throws Exception {
    AbilityConstant arrow =
        abilityService.convert(load("constants_abilities.json")).get("mirana_arrow");

    assertEquals(List.of("Point Target"), arrow.getBehavior());
    assertEquals(List.of(90.0), arrow.getMc());
    assertEquals(List.of(60.0, 150.0, 240.0, 330.0), arrow.getDmg());
    assertEquals(List.of("Strong Dispels Only"), arrow.getDispellable());
  }

  @Test
  void ability_missingAndDirtyFieldsDoNotFailTheLoad() throws Exception {
    Map<String, AbilityConstant> all = abilityService.convert(load("constants_abilities.json"));

    AbilityConstant blink = all.get("antimage_blink");
    assertNull(blink.getBkbpierce());
    assertNull(blink.getDmg_type());

    assertEquals(List.of(), all.get("phantom_lancer_juxtapose").getCd());
    assertEquals(List.of(), all.get("lich_frost_shield").getBkbpierce());
  }

  @Test
  void storedRoundTrip_keepsEveryField() throws Exception {
    Map<String, AbilityConstant> abilities =
        abilityService.convert(load("constants_abilities.json"));
    Map<String, HeroConstant> heroes = heroService.convert(load("constants_heroes.json"));

    assertEquals(abilities, abilityService.convert(roundTrip(abilities)));
    assertEquals(heroes, heroService.convert(roundTrip(heroes)));
  }

  @Test
  void dataStoredBeforeWidening_stillReadsWithNewFieldsNull() throws Exception {
    Map<String, Object> legacyHeroes =
        reader.readValue(
            "{\"1\":{\"id\":\"1\",\"name\":\"npc_dota_hero_antimage\","
                + "\"localized_name\":\"Anti-Mage\"}}",
            new TypeReference<>() {});
    Map<String, Object> legacyAbilities =
        reader.readValue("{\"antimage_blink\":{\"dname\":\"Blink\"}}", new TypeReference<>() {});

    HeroConstant hero = heroService.convert(legacyHeroes).get("1");
    assertEquals("Anti-Mage", hero.getLocalized_name());
    assertNull(hero.getRoles());
    assertNull(hero.getAttack_type());

    AbilityConstant ability = abilityService.convert(legacyAbilities).get("antimage_blink");
    assertEquals("Blink", ability.getDname());
    assertNull(ability.getCd());
    assertNull(ability.getAttrib());
  }

  private Map<String, Object> roundTrip(Map<String, ?> converted) {
    ConstantConverter converter = new ConstantConverter();
    return converter.convertToEntityAttribute(
        converter.convertToDatabaseColumn((Map<String, Object>) converted));
  }

  private Map<String, Object> load(String resource) throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/opendota/" + resource)) {
      return reader.readValue(in, new TypeReference<>() {});
    }
  }
}
