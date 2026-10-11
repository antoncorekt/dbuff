package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores what each hero was doing through the game, from the stored tracks and combat log. */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class ActivityAnalyzer implements ReplayAnalyzer {
  private static final int RADIANT_TEAM = 2;

  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayPlayerRepo playerRepo;
  private final PlayerActivitySegmentRepo segmentRepo;
  private final MatchMapLayout matchMapLayout;
  private final CombatLogReader combatLog;
  private final ReplayAnalysisProperties analysis;

  @Override
  public String name() {
    return "activity";
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
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info("Match {} was not played on the frozen map layout, no activity stored", matchId);
      return;
    }
    List<CombatEvent> events = combatLog.events(matchId);
    if (events.isEmpty()) {
      log.info("Match {} has no stored combat log, no activity stored", matchId);
      return;
    }
    Set<String> heroNames = CombatHeroes.names(events);
    Map<Long, ReplayPlayerDomain> players =
        playerRepo.findByMatchId(matchId).stream()
            .collect(
                Collectors.toMap(
                    ReplayPlayerDomain::getPlayerSlot,
                    Function.identity(),
                    (first, second) -> first));
    ActivitySegmenter segmenter = new ActivitySegmenter(analysis.activityTuning());
    for (ReplayPlayerTrackDomain track : trackRepo.findByMatchId(matchId)) {
      if (track.getSampleCount() == null || track.getSampleCount() == 0) {
        continue;
      }
      ReplayPlayerDomain player = players.get(track.getPlayerSlot());
      String hero = player == null ? null : CombatHeroes.nameOf(player, heroNames);
      if (hero == null) {
        log.info("No combat-log hero for slot {} of match {}", track.getPlayerSlot(), matchId);
        continue;
      }
      List<ActivitySegment> segments =
          segmenter.segment(toTrack(track, player), hero, events, layout);
      segmentRepo.saveAll(rows(matchId, track.getPlayerSlot(), segments));
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    segmentRepo.deleteByMatchId(matchId);
  }

  private static ActivitySegmenter.Track toTrack(
      ReplayPlayerTrackDomain track, ReplayPlayerDomain player) {
    Zone.Half half = player.getTeamNum() == RADIANT_TEAM ? Zone.Half.RADIANT : Zone.Half.DIRE;
    return new ActivitySegmenter.Track(
        half,
        track.getStartSeconds(),
        track.getStepSeconds(),
        track.getX(),
        track.getY(),
        track.getLevel(),
        track.getFlags());
  }

  private List<PlayerActivitySegmentDomain> rows(
      long matchId, long slot, List<ActivitySegment> segments) {
    return segments.stream()
        .map(
            s ->
                PlayerActivitySegmentDomain.builder()
                    .matchId(matchId)
                    .playerSlot(slot)
                    .startSeconds(s.startSeconds())
                    .endSeconds(s.endSeconds())
                    .activity(s.activity().name())
                    .zone(s.zone().name())
                    .lane(s.lane() == null ? null : s.lane().name())
                    .side(s.side() == null ? null : s.side().name())
                    .analysisVersion(version())
                    .build())
        .toList();
  }
}
