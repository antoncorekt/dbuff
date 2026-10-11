package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores what heroes gained, lost, cast and learned, on OpenDota's player slots. */
@Component
@RequiredArgsConstructor
public class ReplayStateChangeExtractor implements ReplayExtractor {
  private final ReplayStateChangeRepo repo;

  @Override
  public String name() {
    return "state-changes";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    if (facts.stateChanges() == null) {
      return;
    }
    Map<Integer, ReplayPlayerFact> players = facts.playersById();
    repo.saveAll(
        facts.stateChanges().changes().stream()
            .filter(change -> players.containsKey(change.replayPlayerId()))
            .map(
                change ->
                    ReplayStateChangeDomain.builder()
                        .matchId(match.getId())
                        .playerSlot(players.get(change.replayPlayerId()).playerSlot())
                        .gameSeconds(change.gameSecond())
                        .kind(change.kind().name())
                        .name(change.name())
                        .slotIndex(change.slotIndex())
                        .readyAtSeconds(change.readyAtSeconds())
                        .cooldownLength(change.cooldownLength())
                        .abilityLevel(change.level())
                        .build())
            .toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
