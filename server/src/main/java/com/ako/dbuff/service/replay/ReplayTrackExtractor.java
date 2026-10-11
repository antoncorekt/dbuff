package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores where every player was, and how the hero was doing, every few seconds. */
@Component
@RequiredArgsConstructor
public class ReplayTrackExtractor implements ReplayExtractor {
  private final ReplayPlayerTrackRepo repo;

  @Override
  public String name() {
    return "tracks";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    SampleFacts samples = facts.samples();
    if (samples == null) {
      return;
    }
    Map<Integer, ReplayPlayerFact> players = facts.playersById();
    List<ReplayPlayerTrackDomain> rows =
        samples.series().stream()
            .filter(series -> players.containsKey(series.replayPlayerId()))
            .map(
                series ->
                    ReplayPlayerTrackDomain.builder()
                        .matchId(match.getId())
                        .playerSlot(players.get(series.replayPlayerId()).playerSlot())
                        .startSeconds(samples.startSeconds())
                        .stepSeconds(samples.stepSeconds())
                        .sampleCount(series.x().length)
                        .x(series.x())
                        .y(series.y())
                        .hpPercent(series.hpPercent())
                        .manaPercent(series.manaPercent())
                        .mana(series.mana())
                        .level(series.level())
                        .flags(series.flags())
                        .build())
            .toList();
    repo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
