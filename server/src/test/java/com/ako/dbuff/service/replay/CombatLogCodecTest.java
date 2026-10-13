package com.ako.dbuff.service.replay;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatLogCodecTest {

  @Test
  void decode_returnsWhatWasEncodedIncludingNegativeTimesAndEmptyNames() {
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.DAMAGE,
                -12.34,
                "npc_dota_hero_sniper",
                "npc_dota_hero_axe",
                "sniper_assassinate",
                CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
                1200,
                340,
                0,
                0,
                0,
                0,
                0),
            new CombatEvent(
                CombatEventKind.GOLD,
                61.5,
                "",
                "npc_dota_hero_axe",
                "",
                2,
                85,
                0,
                12,
                0,
                -6700,
                7100,
                0),
            new CombatEvent(
                CombatEventKind.DEATH,
                1969.9,
                "npc_dota_hero_enchantress",
                "npc_dota_hero_sven",
                "",
                3,
                0,
                0,
                0,
                0,
                0,
                0,
                0b1110));

    List<CombatEvent> decoded = CombatLogCodec.decode(CombatLogCodec.encode(events));

    assertThat(decoded).isEqualTo(events);
  }

  @Test
  void encode_storesEachDistinctNameOnceSoALongLogStaysSmall() {
    List<CombatEvent> many =
        java.util.stream.IntStream.range(0, 30_000)
            .mapToObj(
                i ->
                    new CombatEvent(
                        CombatEventKind.DAMAGE,
                        i / 10.0,
                        "npc_dota_hero_terrorblade",
                        "npc_dota_hero_phantom_assassin",
                        "terrorblade_sunder",
                        3,
                        i % 300,
                        1000 - i % 1000,
                        0,
                        0,
                        0,
                        0,
                        0))
            .toList();

    byte[] blob = CombatLogCodec.encode(many);

    assertThat(blob.length).isLessThan(400_000);
    assertThat(CombatLogCodec.decode(blob)).hasSize(30_000);
  }

  @Test
  void encode_refusesWhatDoesNotFitTheFormatInsteadOfCorruptingIt() {
    CombatEvent badMask =
        new CombatEvent(CombatEventKind.DEATH, 1, "", "", "", 0, 0, 0, 0, 0, 0, 0, 1 << 16);
    CombatEvent badPlace =
        new CombatEvent(CombatEventKind.GOLD, 1, "", "", "", 0, 0, 0, 0, 0, 40_000, 0, 0);
    List<CombatEvent> tooManyNames =
        java.util.stream.IntStream.range(0, 65_536)
            .mapToObj(
                i ->
                    new CombatEvent(
                        CombatEventKind.ABILITY, 1, "hero" + i, "", "", 0, 0, 0, 0, 0, 0, 0, 0))
            .toList();

    assertThatThrownBy(() -> CombatLogCodec.encode(List.of(badMask)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CombatLogCodec.encode(List.of(badPlace)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CombatLogCodec.encode(tooManyNames))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names");
  }

  @Test
  void decode_refusesABlobOfAnotherVersion() throws Exception {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(raw)) {
      out.writeInt(CombatLogCodec.VERSION + 1);
    }

    assertThatThrownBy(() -> CombatLogCodec.decode(Zstd.compress(raw.toByteArray())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void decode_stillReadsAVersionOneBlob_andVersionTellsThemApart() throws Exception {
    byte[] old = CombatLogBlobs.versionOne();

    assertThat(CombatLogCodec.decode(old)).isEmpty();
    assertThat(CombatLogCodec.version(old)).isEqualTo(1);
    assertThat(CombatLogCodec.version(CombatLogCodec.encode(List.of())))
        .isEqualTo(CombatLogCodec.VERSION);
  }

  @Test
  void whitelist_keepsNamedModifiersAndAnythingThatStuns() {
    assertThat(ModifierWhitelist.keeps("modifier_black_king_bar_immune", 0)).isTrue();
    assertThat(ModifierWhitelist.keeps("modifier_teleporting", 0)).isTrue();
    assertThat(ModifierWhitelist.keeps("modifier_some_hero_ability_stun", 1.5)).isTrue();
    assertThat(ModifierWhitelist.keeps("modifier_rune_doubledamage", 0)).isTrue();
    assertThat(ModifierWhitelist.keeps("modifier_item_buff", 0)).isFalse();
  }
}
