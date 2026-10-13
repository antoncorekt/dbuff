package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores what heroes gained, lost, cast and learned, on OpenDota's player slots. */
@Slf4j
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
    List<ReplayStateChangeDomain> rows = new ArrayList<>();
    int dropped = 0;
    for (StateChange change : facts.stateChanges().changes()) {
      ReplayPlayerFact player = players.get(change.replayPlayerId());
      if (player == null) {
        dropped++;
        continue;
      }
      boolean cooldown = change.kind() == StateChangeKind.COOLDOWN;
      boolean level = change.kind() == StateChangeKind.ABILITY_LEVEL;
      boolean cost = change.kind() == StateChangeKind.MANA_COST;
      boolean charges = change.kind() == StateChangeKind.ITEM_CHARGES;
      rows.add(
          ReplayStateChangeDomain.builder()
              .matchId(match.getId())
              .playerSlot(player.playerSlot())
              .gameSeconds(change.gameSecond())
              .kind(change.kind().name())
              .name(change.name())
              .slotIndex(change.slotIndex())
              .readyAtSeconds(cooldown ? change.readyAtSeconds() : null)
              .cooldownLength(cooldown ? change.cooldownLength() : null)
              .abilityLevel(level ? change.level() : null)
              .manaCost(cost ? change.manaCost() : null)
              .charges(charges ? change.charges() : null)
              .build());
    }
    if (dropped > 0) {
      log.warn(
          "Match {}: {} state changes belong to players not on the roster", match.getId(), dropped);
    }
    repo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
