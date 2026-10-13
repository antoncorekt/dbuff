package com.ako.dbuff.service.replay.analysis.creep;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreepValuesTest {

  private static CreepValues parse(String json) {
    CreepValues values = lazy(json);
    values.of(CreepClass.LANE_MELEE);
    return values;
  }

  private static CreepValues lazy(String json) {
    return new CreepValues(
        () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }

  @Test
  void theShippedFileLoadsAndHoldsSaneValuesForTheLaneCreeps() {
    CreepValues values = new CreepValues("replay/creep-values.json");

    var melee = values.of(CreepClass.LANE_MELEE).orElseThrow();
    var ranged = values.of(CreepClass.LANE_RANGED).orElseThrow();
    var siege = values.of(CreepClass.LANE_SIEGE).orElseThrow();
    assertThat(melee.gold()).isBetween(30, 50);
    assertThat(ranged.gold()).isGreaterThan(melee.gold());
    assertThat(siege.gold()).isGreaterThan(ranged.gold());
    assertThat(values.of(CreepClass.LANE_FLAGBEARER).orElseThrow().bonusGold()).isPositive();
    assertThat(values.of(CreepClass.NEUTRAL_ANCIENT).orElseThrow().xp())
        .isGreaterThan(values.of(CreepClass.NEUTRAL).orElseThrow().xp());
    assertThat(values.ofUnit("npc_dota_neutral_black_dragon")).isPresent();
    assertThat(values.of(CreepClass.LANE_MEGA)).as("never measured").isEmpty();
  }

  @Test
  void aClassAndAUnitAreRead() {
    CreepValues values =
        parse(
            "{\"status\": \"s\", \"classes\": {\"LANE_MELEE\": {\"gold\": 38, \"goldSamples\": 5,"
                + " \"xp\": 57, \"xpSamples\": 3}},"
                + " \"neutralUnits\": {\"npc_dota_neutral_kobold\": {\"gold\": 7,"
                + " \"goldSamples\": 5}}}");

    assertThat(values.of(CreepClass.LANE_MELEE).orElseThrow().xp()).isEqualTo(57);
    assertThat(values.ofUnit("npc_dota_neutral_kobold").orElseThrow().xp()).isNull();
    assertThat(values.ofUnit("npc_dota_neutral_other")).isEmpty();
  }

  @Test
  void anUnknownClassIsRejectedWithItNamed() {
    assertThatThrownBy(
            () ->
                parse(
                    "{\"status\": \"s\", \"classes\": {\"LANE_HUGE\": {\"gold\": 1,"
                        + " \"goldSamples\": 1}}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LANE_HUGE");
  }

  @Test
  void aLaneUnitUnderTheCampUnitsIsRejected() {
    assertThatThrownBy(
            () ->
                parse(
                    "{\"status\": \"s\", \"classes\": {\"LANE_MELEE\": {\"gold\": 1,"
                        + " \"goldSamples\": 1}}, \"neutralUnits\":"
                        + " {\"npc_dota_creep_goodguys_melee\": {\"gold\": 1, \"goldSamples\":"
                        + " 1}}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("npc_dota_creep_goodguys_melee");
  }

  @Test
  void aNegativeValueAMissingStatusAndHalfAnXpPairAreRejected() {
    assertThatThrownBy(
            () ->
                parse(
                    "{\"status\": \"s\", \"classes\": {\"LANE_MELEE\": {\"gold\": -1,"
                        + " \"goldSamples\": 1}}}"))
        .hasMessageContaining("LANE_MELEE");
    assertThatThrownBy(
            () -> parse("{\"classes\": {\"LANE_MELEE\": {\"gold\": 1, \"goldSamples\": 1}}}"))
        .hasMessageContaining("status");
    assertThatThrownBy(
            () ->
                parse(
                    "{\"status\": \"s\", \"classes\": {\"LANE_MELEE\": {\"gold\": 1,"
                        + " \"goldSamples\": 1, \"xp\": 5}}}"))
        .hasMessageContaining("xpSamples");
  }

  @Test
  void aDuplicateKeyIsRejected_andAnUnknownFieldIsIgnored() {
    assertThatThrownBy(() -> parse("{\"status\": \"s\", \"status\": \"t\", \"classes\": {}}"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            parse(
                    "{\"status\": \"s\", \"classes\": {\"LANE_MELEE\": {\"gold\": 1,"
                        + " \"goldSamples\": 1, \"golt\": 2}}}")
                .of(CreepClass.LANE_MELEE))
        .isPresent();
  }

  @Test
  void aBadFileIsNotReadUntilTheFirstUse() {
    CreepValues values = lazy("not json");

    assertThatThrownBy(() -> values.of(CreepClass.LANE_MELEE))
        .isInstanceOf(IllegalStateException.class);
  }
}
