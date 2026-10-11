package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores who the replay says played, tied to OpenDota's slots. */
@Component
@RequiredArgsConstructor
public class ReplayPlayerExtractor implements ReplayExtractor {
  private final ReplayPlayerRepo repo;

  @Override
  public String name() {
    return "players";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    repo.deleteByMatchId(match.getId());
    List<ReplayPlayerDomain> rows =
        facts.players().stream()
            .map(
                p ->
                    ReplayPlayerDomain.builder()
                        .matchId(match.getId())
                        .playerSlot(p.playerSlot())
                        .replayPlayerId(p.replayPlayerId())
                        .teamNum(p.teamNum())
                        .accountId(p.accountId())
                        .heroEntityName(p.heroEntityName())
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
