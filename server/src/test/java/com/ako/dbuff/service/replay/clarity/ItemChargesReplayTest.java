package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import com.ako.dbuff.service.replay.StateChange;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the stored charges of the Magic Stick and Wand against the game's own item uses. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class ItemChargesReplayTest {

  private static final Set<String> STICKS = Set.of("item_magic_stick", "item_magic_wand");

  @Test
  void abilityDraftMatch_chargesRiseWithinTheLimitsAndDropWithEveryUse() {
    check("9038725154", TestModes.ABILITY_DRAFT);
  }

  @Test
  void allPickMatch_chargesRiseWithinTheLimitsAndDropWithEveryUse() {
    check("9040892342", TestModes.ALL_PICK);
  }

  private void check(String match, GameModeProfile mode) {
    ReplayFacts facts =
        Parsers.standard()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match + ".dem"), mode, () -> false);
    List<StateChange> all = facts.stateChanges().changes();
    List<StateChange> charges =
        all.stream().filter(c -> c.kind() == StateChangeKind.ITEM_CHARGES).toList();

    assertThat(charges).isNotEmpty().allMatch(c -> STICKS.contains(c.name()));
    assertThat(charges)
        .allSatisfy(
            c ->
                assertThat(c.charges())
                    .isBetween(0, c.name().equals("item_magic_stick") ? 10 : 20));
    assertThat(charges).anyMatch(c -> c.charges() > 0);

    int heroesChecked = 0;
    for (ReplayPlayerFact player : facts.players()) {
      List<StateChange> own =
          all.stream()
              .filter(
                  c -> c.replayPlayerId() == player.replayPlayerId() && STICKS.contains(c.name()))
              .toList();
      long used =
          own.stream()
              .filter(
                  c ->
                      c.kind() == StateChangeKind.COOLDOWN
                          && c.gameSecond() > 0
                          && c.cooldownLength() >= 0)
              .count();
      long logged =
          facts.combatLog().events().stream()
              .filter(
                  e ->
                      e.kind() == CombatEventKind.ITEM
                          && STICKS.contains(e.inflictor())
                          && e.gameSecond() > 0
                          && HeroNames.matches(player.heroEntityName(), e.attacker()))
              .count();
      assertThat(used)
          .as("%s cooldowns against combat-log uses", player.heroEntityName())
          .isBetween(logged - 1, logged + 1);
      heroesChecked += used > 0 ? 1 : 0;
      assertThat(own.stream().filter(c -> c.kind() == StateChangeKind.ITEM_CHARGES).toList())
          .as("a use is followed by the count falling")
          .satisfiesAnyOf(
              list -> assertThat(list).isEmpty(),
              list -> assertThat(list).anyMatch(c -> c.charges() == 0));
    }
    assertThat(heroesChecked).isGreaterThan(2);
  }
}
