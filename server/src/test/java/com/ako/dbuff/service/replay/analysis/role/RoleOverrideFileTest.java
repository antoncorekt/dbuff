package com.ako.dbuff.service.replay.analysis.role;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleOverrideFileTest {

  private static RoleOverrideFile parse(String json) {
    return new RoleOverrideFile(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }

  @Test
  void theShippedFileLoadsAndCarriesTheWellKnownEntries() {
    RoleOverrideFile file = new RoleOverrideFile("replay/role-overrides.json");

    assertThat(file.forAbility("axe_berserkers_call"))
        .containsEntry(RoleFunction.DISABLE, 0.5)
        .containsEntry(RoleFunction.INITIATION, 0.6);
    assertThat(file.forAbility("furion_teleportation")).containsKey(RoleFunction.ROAMING);
    assertThat(file.forAbility("dazzle_shallow_grave")).containsKey(RoleFunction.SAVE_HEAL);
    assertThat(file.forAbility("sandking_epicenter")).containsKey(RoleFunction.DAMAGE);
    assertThat(file.forAbility("not_in_the_file")).isEmpty();
  }

  @Test
  void abilitiesAndHeroesAreRead() {
    RoleOverrideFile file =
        parse(
            "{\"abilities\": {\"a_b\": {\"DISABLE\": 0.3}},"
                + " \"heroes\": {\"2\": {\"DURABILITY\": 1}}}");

    assertThat(file.forAbility("a_b")).containsEntry(RoleFunction.DISABLE, 0.3);
    assertThat(file.forHero(2)).containsEntry(RoleFunction.DURABILITY, 1.0);
    assertThat(file.forHero(3)).isEmpty();
  }

  @Test
  void anUnknownFunctionIsRejectedWithTheEntryNamed() {
    assertThatThrownBy(() -> parse("{\"abilities\": {\"a_b\": {\"STUNNING\": 0.3}}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("a_b")
        .hasMessageContaining("STUNNING");
  }

  @Test
  void aFunctionNameInTheWrongCaseIsRejected() {
    assertThatThrownBy(() -> parse("{\"abilities\": {\"a_b\": {\"disable\": 0.3}}}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anAbilityNameOfTheWrongFormatIsRejected() {
    for (String name :
        new String[] {"Axe Berserkers Call", "axe", "Axe_Call", "axe__call", "_a_b"}) {
      assertThatThrownBy(() -> parse("{\"abilities\": {\"" + name + "\": {\"DISABLE\": 0.3}}}"))
          .as(name)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ability name");
    }
  }

  @Test
  void aHeroIdThatIsNotANumberIsRejected() {
    assertThatThrownBy(() -> parse("{\"heroes\": {\"axe\": {\"DISABLE\": 0.3}}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hero id");
  }

  @Test
  void valuesOutsideZeroToOneAnEmptyEntryAndUnknownSectionsAreRejected() {
    for (String json :
        new String[] {
          "{\"abilities\": {\"a_b\": {\"DISABLE\": 1.5}}}",
          "{\"abilities\": {\"a_b\": {\"DISABLE\": 0}}}",
          "{\"abilities\": {\"a_b\": {\"DISABLE\": null}}}",
          "{\"abilities\": {\"a_b\": {}}}",
          "{\"items\": {}}"
        }) {
      assertThatThrownBy(() -> parse(json)).as(json).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void aDuplicatedEntryIsRejected() {
    assertThatThrownBy(
            () ->
                parse("{\"abilities\": {\"a_b\": {\"DISABLE\": 0.3}, \"a_b\": {\"DAMAGE\": 0.3}}}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aMissingFileStopsTheStartup() {
    assertThatThrownBy(() -> new RoleOverrideFile("replay/none.json"))
        .isInstanceOf(IllegalStateException.class);
  }
}
