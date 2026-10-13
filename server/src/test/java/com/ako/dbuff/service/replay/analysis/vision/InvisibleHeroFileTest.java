package com.ako.dbuff.service.replay.analysis.vision;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvisibleHeroFileTest {
  private static InvisibleHeroFile of(String json) {
    return new InvisibleHeroFile(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }

  private static final String ITEMS = "\"items\": [\"item_glimmer_cape\"]";

  @Test
  void theCheckedInFileLoads_withHeroesAndItems() {
    InvisibleHeroFile file = new InvisibleHeroFile("replay/invisible-heroes.json");

    assertThat(file.abilities()).containsKeys("clinkz_wind_walk", "riki_permanent_invisibility");
    assertThat(file.abilities().get("riki_permanent_invisibility").passive()).isTrue();
    assertThat(file.abilities().get("clinkz_wind_walk").passive()).isFalse();
    assertThat(file.items()).contains("item_silver_edge", "item_glimmer_cape");
  }

  @Test
  void aValidFileGivesAbilitiesByNameWithTheirHero() {
    InvisibleHeroFile file =
        of(
            "{\"heroes\": {\"npc_dota_hero_nyx_assassin\": {\"ability\":"
                + " \"nyx_assassin_vendetta\", \"mode\": \"ACTIVE\"}}, "
                + ITEMS
                + "}");

    assertThat(file.abilities().get("nyx_assassin_vendetta").hero())
        .isEqualTo("npc_dota_hero_nyx_assassin");
  }

  @Test
  void aBadEntryStopsTheFileWithTheEntryNamed() {
    assertThatThrownBy(
            () ->
                of(
                    "{\"heroes\": {\"riki\": {\"ability\": \"riki_x_y\", \"mode\": \"ACTIVE\"}}, "
                        + ITEMS
                        + "}"))
        .hasMessageContaining("hero key 'riki'");
    assertThatThrownBy(
            () ->
                of(
                    "{\"heroes\": {\"npc_dota_hero_riki\": {\"ability\": \"clinkz_wind_walk\","
                        + " \"mode\": \"ACTIVE\"}}, "
                        + ITEMS
                        + "}"))
        .hasMessageContaining("ability 'clinkz_wind_walk' of another hero");
    assertThatThrownBy(
            () ->
                of(
                    "{\"heroes\": {\"npc_dota_hero_riki\": {\"ability\": \"riki_smoke_screen\","
                        + " \"mode\": \"ALWAYS\"}}, "
                        + ITEMS
                        + "}"))
        .hasMessageContaining("mode 'ALWAYS'");
    assertThatThrownBy(() -> of("{\"heroes\": {}, " + ITEMS + "}"))
        .hasMessageContaining("no heroes");
    assertThatThrownBy(
            () ->
                of(
                    "{\"heroes\": {\"npc_dota_hero_riki\": {\"ability\": \"riki_smoke_screen\","
                        + " \"mode\": \"ACTIVE\"}}, \"items\": [\"glimmer\"]}"))
        .hasMessageContaining("item 'glimmer'");
    assertThatThrownBy(() -> of("{\"bonus\": {}}")).hasMessageContaining("unknown section 'bonus'");
    assertThatThrownBy(() -> of("{not json")).hasMessageContaining("cannot be read");
  }
}
