package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds the fights of a match and how each player took part, from the stored data. */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class FightAnalyzer implements ReplayAnalyzer {
  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayParseRunRepo runRepo;
  private final PlayerActivitySegmentRepo segmentRepo;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final CombatLogReader combatLog;
  private final MapLayouts mapLayouts;
  private final ReplayProcessingProperties processing;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "fights";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    List<ReplayPlayerDomain> roster = playerRepo.findByMatchId(matchId);
    Map<String, Integer> playerOf = CombatHeroes.playerIndexByHero(events, roster);
    if (events.isEmpty() || playerOf.isEmpty()) {
      log.info("Match {} has no combat log or heroes to find fights in", matchId);
      return;
    }
    Map<Integer, FightAssessor.Trace> traces = traces(matchId);
    Map<Integer, List<ActivitySegment>> activities = activities(matchId);
    MapLayout layout = layout(matchId);
    List<FightDetector.Detected> detected =
        new FightDetector(properties.fightTuning()).detect(events, playerOf);
    FightAssessor assessor =
        new FightAssessor(
            properties.getFightParticipationRadius(), properties.getFightWinMarginPercent());
    for (int index = 0; index < detected.size(); index++) {
      FightDetector.Detected fight = detected.get(index);
      FightAssessor.Assessment assessment = assessor.assess(fight, traces, activities);
      fightRepo.save(fightRow(matchId, index, fight, assessment, layout));
      fightPlayerRepo.saveAll(playerRows(matchId, index, assessment));
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    fightRepo.deleteByMatchId(matchId);
    fightPlayerRepo.deleteByMatchId(matchId);
  }

  private Map<Integer, FightAssessor.Trace> traces(long matchId) {
    Map<Integer, FightAssessor.Trace> traces = new HashMap<>();
    for (ReplayPlayerTrackDomain t : trackRepo.findByMatchId(matchId)) {
      if (t.getSampleCount() == null || t.getSampleCount() == 0 || t.getX().length == 0) {
        continue;
      }
      traces.put(
          PlayerSlots.index(t.getPlayerSlot()),
          new FightAssessor.Trace(
              t.getStartSeconds(),
              t.getStepSeconds(),
              t.getX(),
              t.getY(),
              t.getLevel(),
              t.getFlags()));
    }
    return traces;
  }

  private Map<Integer, List<ActivitySegment>> activities(long matchId) {
    Map<Integer, List<ActivitySegment>> bySlot = new HashMap<>();
    for (PlayerActivitySegmentDomain s : segmentRepo.findByMatchId(matchId)) {
      bySlot
          .computeIfAbsent(PlayerSlots.index(s.getPlayerSlot()), k -> new ArrayList<>())
          .add(
              new ActivitySegment(
                  s.getStartSeconds(),
                  s.getEndSeconds(),
                  Activity.valueOf(s.getActivity()),
                  Zone.Kind.valueOf(s.getZone()),
                  s.getLane() == null ? null : Zone.Lane.valueOf(s.getLane()),
                  s.getSide() == null ? null : ActivitySegment.Side.valueOf(s.getSide())));
    }
    return bySlot;
  }

  private MapLayout layout(long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, processing.getParserVersion())
        .map(ReplayParseRunDomain::getMapVersion)
        .flatMap(mapLayouts::forVersion)
        .orElse(null);
  }

  private FightDomain fightRow(
      long matchId,
      int index,
      FightDetector.Detected fight,
      FightAssessor.Assessment a,
      MapLayout layout) {
    Zone zone =
        layout == null || a.centerX() == null ? null : layout.zone(a.centerX(), a.centerY());
    return FightDomain.builder()
        .matchId(matchId)
        .fightIndex(index)
        .type(fight.type().name())
        .startSeconds(fight.start())
        .endSeconds(fight.end())
        .firstDeathSeconds(fight.firstDeath())
        .centerX(a.centerX())
        .centerY(a.centerY())
        .zone(zone == null ? null : zone.kind().name())
        .lane(zone == null || zone.lane() == null ? null : zone.lane().name())
        .radiantDeaths(a.radiantDeaths())
        .direDeaths(a.direDeaths())
        .radiantParticipants(a.radiantParticipants())
        .direParticipants(a.direParticipants())
        .radiantHeroDamage(a.radiantDamage())
        .direHeroDamage(a.direDamage())
        .winner(a.winner())
        .analysisVersion(version())
        .build();
  }

  private List<FightPlayerDomain> playerRows(
      long matchId, int index, FightAssessor.Assessment assessment) {
    return assessment.parts().stream()
        .map(
            p ->
                FightPlayerDomain.builder()
                    .matchId(matchId)
                    .fightIndex(index)
                    .playerSlot(PlayerSlots.slot(p.player()))
                    .presence(p.presence().name())
                    .damageDealt(p.damageDealt())
                    .damageTaken(p.damageTaken())
                    .deaths(p.deaths())
                    .distanceToCenter(p.distanceToCenter())
                    .activityAtStart(p.activityAtStart())
                    .analysisVersion(version())
                    .build())
        .toList();
  }
}
