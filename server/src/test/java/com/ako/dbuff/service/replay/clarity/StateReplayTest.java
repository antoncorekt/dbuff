package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.StateChange;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the recorded cooldowns and inventory against what OpenDota counts for the same hero. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class StateReplayTest {

  private static final int SNIPER = 0;

  private final ReplayFacts facts =
      Parsers.standard()
          .parse(
              Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"),
              TestModes.ABILITY_DRAFT,
              () -> false);

  @Test
  void aCooldownIsRecordedForEveryUseOpenDotaCounts() {
    assertThat(cooldowns("furion_teleportation")).as("OpenDota ability_uses 37").isEqualTo(37);
    assertThat(cooldowns("sniper_take_aim")).as("OpenDota ability_uses 11").isEqualTo(11);
    assertThat(cooldowns("oracle_false_promise")).as("OpenDota ability_uses 10").isEqualTo(10);
    assertThat(cooldowns("terrorblade_conjure_image")).as("OpenDota ability_uses 64").isEqualTo(64);
    assertThat(cooldowns("item_dust")).as("combat-log item uses 8").isEqualTo(8);
    assertThat(cooldowns("item_tpscroll")).as("OpenDota item_uses 9").isEqualTo(9);
  }

  @Test
  void aCooldownEndsAfterItWasSeenAndNoLaterThanItsLengthWhenTheLengthIsKnown() {
    List<StateChange> cooldowns =
        changes(StateChangeKind.COOLDOWN).stream()
            .filter(c -> c.gameSecond() > 0 && c.cooldownLength() >= 0)
            .toList();

    assertThat(cooldowns).isNotEmpty();
    assertThat(cooldowns)
        .allSatisfy(
            c -> {
              assertThat(c.readyAtSeconds()).isGreaterThan(c.gameSecond());
              if (c.cooldownLength() > 0) {
                assertThat(c.readyAtSeconds() - c.gameSecond())
                    .isLessThanOrEqualTo(c.cooldownLength() + 1);
              }
            });
  }

  @Test
  void theStartingItemsAreSeenBeforeTheHornAndABuildIsTrackedThroughItsParts() {
    List<StateChange> items = changes(StateChangeKind.ITEM_ADDED);

    assertThat(items)
        .filteredOn(c -> c.gameSecond() < 0)
        .extracting(StateChange::name)
        .contains("item_tango", "item_wraith_band", "item_tpscroll");
    assertThat(items)
        .extracting(StateChange::name)
        .contains("item_power_treads", "item_boots_of_elves");
    assertThat(changes(StateChangeKind.ITEM_REMOVED))
        .extracting(StateChange::name)
        .contains("item_boots_of_elves", "item_gloves");
  }

  @Test
  void skillLevelsAreRecordedWhenTheyRise() {
    assertThat(changes(StateChangeKind.ABILITY_LEVEL))
        .filteredOn(c -> c.name().equals("sniper_take_aim"))
        .extracting(StateChange::level)
        .containsExactly(1, 2, 3, 4);
  }

  @Test
  void manaCostsAreRecordedAndMoveWithTheSkillLevel() {
    assertThat(changes(StateChangeKind.MANA_COST))
        .filteredOn(c -> c.name().equals("furion_teleportation"))
        .extracting(StateChange::manaCost)
        .containsExactly(50, 60, 70, 80);
    assertThat(changes(StateChangeKind.MANA_COST))
        .filteredOn(c -> c.name().equals("item_tpscroll"))
        .extracting(StateChange::manaCost)
        .containsExactly(75);
    assertThat(Arrays.stream(facts.samples().series().get(SNIPER).mana()).max().orElse(0))
        .isGreaterThan(300);
  }

  @Test
  void attackRangeIsSampledInWorldUnits() {
    int[] ranges = facts.samples().series().get(SNIPER).attackRange();

    assertThat(Arrays.stream(ranges).filter(r -> r > 0))
        .isNotEmpty()
        .allMatch(r -> r >= 100 && r <= 1500);
  }

  private long cooldowns(String name) {
    return changes(StateChangeKind.COOLDOWN).stream()
        .filter(c -> c.name().equals(name) && c.gameSecond() > 0 && c.cooldownLength() >= 0)
        .count();
  }

  private List<StateChange> changes(StateChangeKind kind) {
    return facts.stateChanges().changes().stream()
        .filter(c -> c.replayPlayerId() == SNIPER && c.kind() == kind)
        .toList();
  }
}
