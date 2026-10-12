package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A hero and ability lookup over maps, for the mode contributor tests. */
final class ModeTestKits implements HeroKits {
  private final Map<Long, HeroConstant> heroes;
  private final Map<String, AbilityConstant> abilities;

  ModeTestKits(Map<Long, HeroConstant> heroes, Map<String, AbilityConstant> abilities) {
    this.heroes = heroes;
    this.abilities = abilities;
  }

  /** The abilities of the role profile fixture, by name. */
  static Map<String, AbilityConstant> fixtureAbilities() {
    try (InputStream in =
        ModeTestKits.class.getResourceAsStream("/opendota/constants_profile_abilities.json")) {
      return new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readValue(in, new TypeReference<>() {});
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static HeroConstant hero(long id, String name, String... roles) {
    return HeroConstant.builder()
        .id(String.valueOf(id))
        .localized_name(name)
        .roles(List.of(roles))
        .build();
  }

  @Override
  public Optional<HeroConstant> hero(long heroId) {
    return Optional.ofNullable(heroes.get(heroId));
  }

  @Override
  public Optional<AbilityConstant> ability(String name) {
    return Optional.ofNullable(abilities.get(name));
  }
}
