package com.ako.dbuff.service.discord.command.autocomplete;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceAccumulatorTest {

  /** Display name -> submitted value, in the shape the constant providers supply. */
  private static final Map<String, String> CANDIDATES =
      Map.of(
          "Blink Dagger", "blink",
          "Black King Bar", "black_king_bar",
          "Battle Fury", "battle_fury",
          "Manta Style", "manta");

  @Test
  void emptyInput_offersEverything() {
    assertThat(ChoiceAccumulator.accumulate("", CANDIDATES)).hasSize(4);
  }

  @Test
  void nullInput_isTreatedAsEmpty() {
    assertThat(ChoiceAccumulator.accumulate(null, CANDIDATES)).hasSize(4);
  }

  @Test
  void singleToken_matchesBySubstringCaseInsensitively() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void matchesDisplayNameNotJustValue() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("King", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Black King Bar");
  }

  @Test
  void secondToken_preservesTheFirstAndCompletesOnlyTheLast() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink, black k", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger, Black King Bar");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void trailingComma_offersEveryRemainingCandidate() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink,", CANDIDATES);

    // Three, not four: Blink Dagger is already in the prefix and is not offered twice.
    assertThat(choices).hasSize(3);
    assertThat(choices).allSatisfy(c -> assertThat(c.getAsString()).startsWith("blink,"));
  }

  @Test
  void alreadyChosenEntriesAreNotOfferedAgain() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink, bl", CANDIDATES);

    // "bl" also matches Blink Dagger, but it is already chosen.
    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void unresolvablePrefixToken_isPreservedVerbatim() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("mystery, blink", CANDIDATES);

    // The user may be mid-edit; deleting their text would be hostile.
    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("mystery,blink");
  }

  @Test
  void noMatches_isEmptyNotAnError() {
    assertThat(ChoiceAccumulator.accumulate("zzzzz", CANDIDATES)).isEmpty();
  }

  @Test
  void resultIsCappedAtDiscordsTwentyFiveChoices() {
    Map<String, String> many = new HashMap<>();
    for (int i = 0; i < 60; i++) {
      many.put("Item Number " + i, "item_" + i);
    }

    assertThat(ChoiceAccumulator.accumulate("Item", many)).hasSize(25);
  }

  @Test
  void choicesExceedingTheValueLengthLimitAreDroppedNotTruncated() {
    // A prefix long enough that appending anything breaks the 100-character value limit.
    String longPrefix = "a".repeat(95);

    List<Command.Choice> choices =
        ChoiceAccumulator.accumulate(longPrefix + ", bli", Map.of("Blink Dagger", "blink"));

    assertThat(choices).isEmpty();
  }

  @Test
  void singleValued_doesNotAccumulate() {
    List<Command.Choice> choices = ChoiceAccumulator.single("blink", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void singleValued_rejectsCommaSeparatedInput() {
    // hero: is single-valued, so a list must match nothing rather than silently take the first.
    assertThat(ChoiceAccumulator.single("blink, black", CANDIDATES)).isEmpty();
  }

  @Test
  void singleValued_emptyInput_offersEverything() {
    assertThat(ChoiceAccumulator.single("", CANDIDATES)).hasSize(4);
  }
}
