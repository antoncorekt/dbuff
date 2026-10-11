package com.ako.dbuff.service.discord.command;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerIdParserTest {

  @Test
  void largestAccountId_isAccepted() {
    assertThat(PlayerIdParser.parse("4294967295")).contains(4294967295L);
  }

  @Test
  void accountId_isReturnedAsIs() {
    assertThat(PlayerIdParser.parse("204429164")).contains(204429164L);
  }

  @Test
  void steam64_isConvertedToTheAccountId() {
    assertThat(PlayerIdParser.parse("76561198164694892")).contains(204429164L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://www.opendota.com/players/204429164",
        "https://www.dotabuff.com/players/204429164",
        "https://stratz.com/players/204429164/overview",
        "https://steamcommunity.com/profiles/76561198164694892",
        "  https://www.dotabuff.com/players/204429164?date=week  "
      })
  void profileLinks_yieldTheAccountId(String link) {
    assertThat(PlayerIdParser.parse(link)).contains(204429164L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Termit",
        "https://steamcommunity.com/id/vanity",
        "0",
        "12abc",
        "99999999999999999999",
        "4294967296",
        "123456789012345678",
        "76561202255233025",
        "https://www.dotabuff.com/matches/123"
      })
  void everythingElse_isRejected(String reference) {
    assertThat(PlayerIdParser.parse(reference)).isEqualTo(Optional.empty());
  }

  @Test
  void parseAll_keepsOrderDropsDuplicatesAndReportsTheInvalid() {
    PlayerIdParser.Result result =
        PlayerIdParser.parseAll(
            List.of("204429164", "76561198164694892", "", "Termit", "279195408"));

    assertThat(result.ids()).containsExactly(204429164L, 279195408L);
    assertThat(result.invalid()).containsExactly("Termit");
  }
}
