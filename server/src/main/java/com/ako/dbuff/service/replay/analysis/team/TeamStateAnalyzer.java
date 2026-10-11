package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
  private static final int STEP_SECONDS = 60;
  private static final int PLAYERS = 10;
  private static final long FIRST_DIRE_SLOT = 128;

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
    double[] radiantLead = radiantLead(timelineRepo.findByMatchId(matchId));
    if (radiantLead == null) {
      log.info("Match {} has no complete net worth timeline, so no team states", matchId);
      return;
    }
    double[] direLead = new double[radiantLead.length];
    for (int i = 0; i < radiantLead.length; i++) {
      direLead[i] = -radiantLead[i];
    }
    TeamStateSegmenter segmenter = new TeamStateSegmenter(properties.teamStateTuning());
    List<GameStateSegmentDomain> rows = new ArrayList<>();
    rows.addAll(rows(matchId, "RADIANT", segmenter.segment(radiantLead, STEP_SECONDS)));
    rows.addAll(rows(matchId, "DIRE", segmenter.segment(direLead, STEP_SECONDS)));
    segmentRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    segmentRepo.deleteByMatchId(matchId);
  }

  /**
   * Radiant's lead at each minute from 0, as a share of both teams' net worth together; null when a
   * minute lacks a player or a net worth, or the minutes have a gap.
   */
  private static double[] radiantLead(List<PlayerTimelineDomain> rows) {
    Map<Integer, long[]> byMinute = new TreeMap<>();
    for (PlayerTimelineDomain row : rows) {
      if (row.getNetworth() == null || row.getGameMinute() == null || row.getPlayerSlot() == null) {
        return null;
      }
      long[] sums = byMinute.computeIfAbsent(row.getGameMinute(), m -> new long[3]);
      sums[row.getPlayerSlot() >= FIRST_DIRE_SLOT ? 1 : 0] += row.getNetworth();
      sums[2]++;
    }
    if (byMinute.isEmpty() || !byMinute.keySet().iterator().next().equals(0)) {
      return null;
    }
    double[] lead = new double[byMinute.size()];
    int expected = 0;
    for (Map.Entry<Integer, long[]> e : byMinute.entrySet()) {
      long[] sums = e.getValue();
      long total = sums[0] + sums[1];
      if (e.getKey() != expected || sums[2] != PLAYERS || total <= 0) {
        return null;
      }
      lead[expected++] = (sums[0] - sums[1]) / (double) total;
    }
    return lead;
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
