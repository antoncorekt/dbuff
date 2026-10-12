package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the activity classifier on a real replay against what OpenDota records. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class ActivityReplayTest {

  @Test
  void deadTimeAndJunglingAgreeWithOpenDota() {
    ReplayFacts facts =
        Parsers.standard()
            .parse(
                Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"),
                TestModes.ABILITY_DRAFT,
                () -> false);
    List<CombatEvent> events = facts.combatLog().events();
    Map<String, Map<Activity, Integer>> seconds = new java.util.HashMap<>();
    ReplayActivities.of(facts)
        .forEach(
            (player, segments) -> {
              Map<Activity, Integer> total = new EnumMap<>(Activity.class);
              segments.forEach(
                  s -> total.merge(s.activity(), s.endSeconds() - s.startSeconds(), Integer::sum));
              seconds.put(ReplayActivities.heroName(events, player.heroEntityName()), total);
            });

    int sniperDead = seconds.get("npc_dota_hero_sniper").getOrDefault(Activity.DEAD, 0);
    assertThat(sniperDead).as("OpenDota: nine deaths, 344 s dead").isBetween(300, 390);
    Map<Activity, Integer> phantom = seconds.get("npc_dota_hero_phantom_assassin");
    assertThat(phantom.get(Activity.JUNGLE_FARM))
        .as("190 neutral kills")
        .isGreaterThan(phantom.get(Activity.LANE_FARM));
    Map<Activity, Integer> terrorblade = seconds.get("npc_dota_hero_terrorblade");
    assertThat(terrorblade.get(Activity.LANE_FARM))
        .as("121 lane creeps, 6 neutrals")
        .isGreaterThan(terrorblade.getOrDefault(Activity.JUNGLE_FARM, 0));
    seconds
        .values()
        .forEach(
            total ->
                assertThat(total.values().stream().mapToInt(i -> i).sum()).isGreaterThan(2000));
  }
}
