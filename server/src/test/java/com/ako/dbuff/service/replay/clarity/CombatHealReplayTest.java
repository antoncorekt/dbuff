package com.ako.dbuff.service.replay.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Checks that heals and the item modifiers reach the stored combat log of real replays. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class CombatHealReplayTest {

  private static final List<String> MATCHES =
      List.of("9037038717", "9038725154", "9040889566", "9040892342");

  @Test
  void everyReplayKeepsHeals_andTheItemModifiersOfTheFixtures() {
    for (String match : MATCHES) {
      List<CombatEvent> events =
          Parsers.standard()
              .parse(
                  Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match + ".dem"),
                  TestModes.ABILITY_DRAFT,
                  () -> false)
              .combatLog()
              .events();

      assertThat(events)
          .as("%s: heal events", match)
          .anyMatch(e -> e.kind() == CombatEventKind.HEAL && e.value() > 0);
      assertThat(events)
          .as("%s: bottle regeneration", match)
          .anyMatch(
              e ->
                  e.kind() == CombatEventKind.MODIFIER_ADD
                      && "modifier_bottle_regeneration".equals(e.inflictor()));
    }
  }
}
