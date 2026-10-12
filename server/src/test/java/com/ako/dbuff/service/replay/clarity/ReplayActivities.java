package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import com.ako.dbuff.service.replay.SampleFacts;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegmenter;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.Zone;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The activity of every player of a parsed replay, worked out as the activity analyzer does. */
final class ReplayActivities {
  private static final MapLayouts LAYOUTS = new MapLayouts(new ObjectMapper());
  private static final ActivitySegmenter SEGMENTER =
      new ActivitySegmenter(new ReplayAnalysisProperties().activityTuning());

  private ReplayActivities() {}

  static Map<ReplayPlayerFact, List<ActivitySegment>> of(ReplayFacts facts) {
    MapLayout layout = LAYOUTS.forVersion(facts.map().version()).orElseThrow();
    List<CombatEvent> events = facts.combatLog().events();
    Map<ReplayPlayerFact, List<ActivitySegment>> result = new HashMap<>();
    for (SampleFacts.PlayerSeries series : facts.samples().series()) {
      ReplayPlayerFact player =
          facts.players().stream()
              .filter(p -> p.replayPlayerId() == series.replayPlayerId())
              .findFirst()
              .orElseThrow();
      var track =
          new ActivitySegmenter.Track(
              player.teamNum() == 2 ? Zone.Half.RADIANT : Zone.Half.DIRE,
              facts.samples().startSeconds(),
              facts.samples().stepSeconds(),
              series.x(),
              series.y(),
              series.level(),
              series.flags());
      result.put(
          player,
          SEGMENTER.segment(track, heroName(events, player.heroEntityName()), events, layout));
    }
    return result;
  }

  static String heroName(List<CombatEvent> events, String entityName) {
    return events.stream()
        .filter(e -> (e.flags() & CombatEvent.ATTACKER_HERO) != 0)
        .map(CombatEvent::attacker)
        .filter(name -> HeroNames.matches(entityName, name))
        .findFirst()
        .orElseThrow();
  }
}
