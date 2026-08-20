package com.ako.dbuff.service.discord.command;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSimilarityTest {

  @Test
  void editDistance_identicalStrings_isZero() {
    assertThat(TextSimilarity.editDistance("blink", "blink")).isZero();
  }

  @Test
  void editDistance_singleSubstitution_isOne() {
    assertThat(TextSimilarity.editDistance("blink", "blonk")).isEqualTo(1);
  }

  @Test
  void editDistance_singleDeletion_isOne() {
    assertThat(TextSimilarity.editDistance("blink", "blnk")).isEqualTo(1);
  }

  @Test
  void editDistance_emptyAgainstNonEmpty_isLength() {
    assertThat(TextSimilarity.editDistance("", "blink")).isEqualTo(5);
    assertThat(TextSimilarity.editDistance("blink", "")).isEqualTo(5);
  }

  @Test
  void closest_findsTheNearestCandidate() {
    List<String> candidates = List.of("add-players", "remove-players", "status");

    assertThat(TextSimilarity.closest("add-player", candidates, 4)).contains("add-players");
    assertThat(TextSimilarity.closest("statu", candidates, 4)).contains("status");
  }

  @Test
  void closest_isCaseInsensitiveButPreservesCandidateCasing() {
    assertThat(TextSimilarity.closest("STATUS", List.of("status"), 4)).contains("status");
  }

  @Test
  void closest_beyondMaxDistance_isEmpty() {
    assertThat(TextSimilarity.closest("zzzzzzzzzz", List.of("status"), 4)).isEmpty();
  }

  @Test
  void closest_emptyCandidates_isEmpty() {
    assertThat(TextSimilarity.closest("status", List.of(), 4)).isEmpty();
  }

  @Test
  void closest_nullOrBlankInput_isEmpty() {
    assertThat(TextSimilarity.closest(null, List.of("status"), 4)).isEmpty();
    assertThat(TextSimilarity.closest("   ", List.of("status"), 4)).isEmpty();
  }
}
