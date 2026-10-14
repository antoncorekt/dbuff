package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Says when each team was ahead, behind or about even, from the net worth OpenDota records for
 * every player each minute. It reads no replay data, so it needs only the stored timelines.
 */
@Slf4j
@Order(40)
@Component
@RequiredArgsConstructor
public class TeamStateAnalyzer implements ReplayAnalyzer {
  private final PlayerTimelineRepo timelineRepo;
  private final GameStateSegmentRepo segmentRepo;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "team-state";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    TeamNetWorth netWorth = TeamNetWorth.of(timelineRepo.findByMatchId(matchId));
    if (netWorth == null) {
      log.info("Match {} has no complete net worth timeline, so no team states", matchId);
      return;
    }
    double[] radiantLead = netWorth.radiantLead();
    double[] direLead = new double[radiantLead.length];
    for (int i = 0; i < radiantLead.length; i++) {
      direLead[i] = -radiantLead[i];
    }
    TeamStateSegmenter segmenter = new TeamStateSegmenter(properties.teamStateTuning());
    List<GameStateSegmentDomain> rows = new ArrayList<>();
    rows.addAll(
        rows(matchId, "RADIANT", segmenter.segment(radiantLead, TeamNetWorth.STEP_SECONDS)));
    rows.addAll(rows(matchId, "DIRE", segmenter.segment(direLead, TeamNetWorth.STEP_SECONDS)));
    segmentRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    segmentRepo.deleteByMatchId(matchId);
  }

  private List<GameStateSegmentDomain> rows(
      long matchId, String team, List<TeamStateSegmenter.Segment> segments) {
    return segments.stream()
        .map(
            s ->
                GameStateSegmentDomain.builder()
                    .matchId(matchId)
                    .team(team)
                    .startSeconds(s.startSeconds())
                    .endSeconds(s.endSeconds())
                    .state(s.state().name())
                    .peakLeadPercent(Math.round(s.peakLead() * 1000) / 10.0)
                    .analysisVersion(version())
                    .build())
        .toList();
  }
}
