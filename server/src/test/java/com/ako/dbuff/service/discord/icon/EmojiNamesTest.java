package com.ako.dbuff.service.discord.icon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmojiNamesTest {

  @Test
  void plainKey_getsOnlyThePrefix() {
    assertThat(EmojiNames.of(IconKind.HERO, "antimage")).isEqualTo("h_antimage");
    assertThat(EmojiNames.of(IconKind.ABILITY, "antimage_blink")).isEqualTo("a_antimage_blink");
    assertThat(EmojiNames.of(IconKind.ITEM, "blink")).isEqualTo("i_blink");
  }

  @Test
  void longKey_isTrimmedToDiscordLimitWithHash() {
    String key = "abyssal_underlord_portal_of_the_ancients_long";
    String name = EmojiNames.of(IconKind.ABILITY, key);

    assertThat(name).hasSize(EmojiNames.MAX_LENGTH).startsWith("a_abyssal_underlord_porta");
    assertThat(name).matches("[a-z0-9_]{2,32}");
  }

  @Test
  void keysThatTrimToTheSameText_stayDifferent() {
    String a = "keeper_of_the_light_spirit_form_illuminate_a";
    String b = "keeper_of_the_light_spirit_form_illuminate_b";

    assertThat(EmojiNames.of(IconKind.ABILITY, a)).isNotEqualTo(EmojiNames.of(IconKind.ABILITY, b));
  }

  @Test
  void unsafeCharacters_areReplacedAndKeepKeysApart() {
    String dash = EmojiNames.of(IconKind.ITEM, "a-b");
    String dot = EmojiNames.of(IconKind.ITEM, "a.b");

    assertThat(dash).matches("i_a_b_[0-9a-f]{6}");
    assertThat(dash).isNotEqualTo(dot);
  }

  @Test
  void name_isStable() {
    assertThat(EmojiNames.of(IconKind.ITEM, "a-b")).isEqualTo(EmojiNames.of(IconKind.ITEM, "a-b"));
  }

  @Test
  void withHash_differsFromThePlainName() {
    assertThat(EmojiNames.withHash(IconKind.HERO, "antimage"))
        .isNotEqualTo(EmojiNames.of(IconKind.HERO, "antimage"))
        .hasSizeLessThanOrEqualTo(EmojiNames.MAX_LENGTH);
  }
}
