package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import com.ako.dbuff.service.replay.SampleFacts;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegmenter;
import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.Zone;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private final MapLayouts layouts = new MapLayouts(new ObjectMapper());
  private final ActivitySegmenter segmenter = new ActivitySegmenter(ActivityTuning.defaults());

  @Test
  void deadTimeAndJunglingAgreeWithOpenDota() {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"), () -> false);
    MapLayout layout = layouts.forVersion(facts.map().version()).orElseThrow();
    List<CombatEvent> events = facts.combatLog().events();

    Map<String, Map<Activity, Integer>> seconds = new java.util.HashMap<>();
    for (SampleFacts.PlayerSeries series : facts.samples().series()) {
      ReplayPlayerFact player =
          facts.players().stream()
              .filter(p -> p.replayPlayerId() == series.replayPlayerId())
              .findFirst()
              .orElseThrow();
      String hero = heroName(events, player.heroEntityName());
      var track =
          new ActivitySegmenter.Track(
              player.teamNum() == 2 ? Zone.Half.RADIANT : Zone.Half.DIRE,
              facts.samples().startSeconds(),
              facts.samples().stepSeconds(),
              series.x(),
              series.y(),
              series.level(),
              series.flags());
      Map<Activity, Integer> total = new EnumMap<>(Activity.class);
      for (ActivitySegment s : segmenter.segment(track, hero, events, layout)) {
        total.merge(s.activity(), s.endSeconds() - s.startSeconds(), Integer::sum);
      }
      seconds.put(hero, total);
    }

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

  private static String heroName(List<CombatEvent> events, String entityName) {
    return events.stream()
        .filter(e -> (e.flags() & CombatEvent.ATTACKER_HERO) != 0)
        .map(CombatEvent::attacker)
        .filter(name -> HeroNames.matches(entityName, name))
        .findFirst()
        .orElseThrow();
  }
}
