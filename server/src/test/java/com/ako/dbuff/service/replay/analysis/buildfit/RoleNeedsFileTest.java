package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleNeedsFileTest {

  private static RoleNeedsFile parse(String json) {
    return new RoleNeedsFile(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }

  @Test
  void theShippedFileHasNeedsForEveryRole() {
    RoleNeedsFile file = new RoleNeedsFile("replay/role-needs.json");

    assertThat(file.forRole(1)).containsKeys(RoleFunction.DAMAGE, RoleFunction.FARM_PRIORITY);
    assertThat(file.forRole(2)).containsKeys(RoleFunction.DAMAGE, RoleFunction.DISABLE);
    assertThat(file.forRole(3)).containsKeys(RoleFunction.DURABILITY, RoleFunction.INITIATION);
    assertThat(file.forRole(4)).containsKeys(RoleFunction.DISABLE, RoleFunction.DAMAGE);
    assertThat(file.forRole(5)).containsKeys(RoleFunction.SAVE_HEAL, RoleFunction.DISABLE);
    assertThat(file.forRole(6)).isEmpty();
  }

  @Test
  void aRoleIsReadWithItsValues() {
    assertThat(parse("{\"1\": {\"DAMAGE\": 0.4}}").forRole(1))
        .containsEntry(RoleFunction.DAMAGE, 0.4);
  }

  @Test
  void anUnknownRoleIsRejectedWithTheEntryNamed() {
    for (String role : new String[] {"0", "6", "caster", "-1", "1.5"}) {
      assertThatThrownBy(() -> parse("{\"" + role + "\": {\"DAMAGE\": 0.4}}"))
          .as(role)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'" + role + "'");
    }
  }

  @Test
  void twoKeysOfTheSameRoleAreRejectedWithBothNamed() {
    for (String other : new String[] {"01", "+1"}) {
      assertThatThrownBy(
              () -> parse("{\"1\": {\"DAMAGE\": 0.4}, \"" + other + "\": {\"DISABLE\": 0.4}}"))
          .as(other)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'1'")
          .hasMessageContaining("'" + other + "'");
    }
  }

  @Test
  void anUnknownFunctionIsRejectedWithTheEntryAndTheFunctionNamed() {
    assertThatThrownBy(() -> parse("{\"2\": {\"STUNNING\": 0.4}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'2'")
        .hasMessageContaining("STUNNING");
  }

  @Test
  void aValueOutsideZeroToOneIsRejected() {
    for (String value : new String[] {"0", "-0.1", "1.1", "null"}) {
      assertThatThrownBy(() -> parse("{\"2\": {\"DAMAGE\": " + value + "}}"))
          .as(value)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DAMAGE");
    }
  }

  @Test
  void aDuplicatedRoleOrFunctionIsRejected() {
    assertThatThrownBy(() -> parse("{\"1\": {\"DAMAGE\": 0.4}, \"1\": {\"DISABLE\": 0.4}}"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> parse("{\"1\": {\"DAMAGE\": 0.4, \"DAMAGE\": 0.5}}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anEmptyEntryAnEmptyFileAndAMissingFileAreRejected() {
    for (String json : new String[] {"{\"1\": {}}", "", "null", "[]", "{\"1\": null}"}) {
      assertThatThrownBy(() -> parse(json))
          .as(json)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("Role needs file test.json: ");
    }
    assertThatThrownBy(() -> new RoleNeedsFile("replay/none.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Role needs file replay/none.json: ");
  }
}
