package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores the Ability Draft: who took which skill when, and what was left on the table. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityDraftExtractor implements ReplayExtractor {
  static final int POOL_SIZE = 48;
  static final int PICKS = 40;
  private static final int ULTIMATE_SLOT = 3;
  private static final int RADIANT = 2;
  private static final double TICKS_PER_SECOND = 30.0;

  private final AbilityDraftPickRepo pickRepo;
  private final AbilityDraftPoolRepo poolRepo;
  private final AbilityNames abilityNames;

  @Override
  public String name() {
    return "ability-draft";
  }

  /** A match with no draft, or one of an unexpected shape, stores nothing rather than a part. */
  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    DraftFacts draft = facts.draft();
    if (draft == null) {
      return;
    }
    if (draft.pool().size() != POOL_SIZE || draft.picks().size() != PICKS) {
      log.warn(
          "Match {} draft has {} skills and {} picks, expected {} and {}; not stored",
          match.getId(),
          draft.pool().size(),
          draft.picks().size(),
          POOL_SIZE,
          PICKS);
      return;
    }
    Map<Integer, ReplayPlayerFact> players = new HashMap<>();
    facts.players().forEach(p -> players.put(p.replayPlayerId(), p));
    List<AbilityDraftPickDomain> picks = pickRows(match.getId(), draft, players);
    pickRepo.saveAll(picks);
    poolRepo.saveAll(poolRows(match.getId(), draft, picks));
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    pickRepo.deleteByMatchId(matchId);
    poolRepo.deleteByMatchId(matchId);
  }

  private List<AbilityDraftPickDomain> pickRows(
      long matchId, DraftFacts draft, Map<Integer, ReplayPlayerFact> players) {
    List<AbilityDraftPickDomain> rows = new ArrayList<>();
    Map<Integer, Integer> picksPerTeam = new HashMap<>();
    int firstTick = draft.picks().getFirst().tick();
    int previousTick = firstTick;
    for (DraftPick pick : draft.picks()) {
      ReplayPlayerFact player = players.get(pick.replayPlayerId());
      if (player == null) {
        throw new IllegalStateException(
            "Draft pick " + pick.order() + " belongs to unknown player " + pick.replayPlayerId());
      }
      DraftPoolEntry entry = draft.pool().get(pick.poolIndex());
      int team = player.teamNum() == RADIANT ? 0 : 1;
      rows.add(
          AbilityDraftPickDomain.builder()
              .matchId(matchId)
              .pickOrder(pick.order())
              .playerSlot(player.playerSlot())
              .team(team)
              .teamPickIndex(picksPerTeam.merge(team, 1, Integer::sum))
              .pickTick(pick.tick())
              .secondsIntoDraft((pick.tick() - firstTick) / TICKS_PER_SECOND)
              .secondsSincePrevPick(
                  pick.order() == 1 ? null : (pick.tick() - previousTick) / TICKS_PER_SECOND)
              .abilityId((long) entry.abilityId())
              .abilityName(abilityNames.nameOf(entry.abilityId()).orElse(null))
              .finalAbilitySlot(entry.finalSlot())
              .ultimate(entry.finalSlot() == ULTIMATE_SLOT)
              .poolRemainingBeforePick(POOL_SIZE - (pick.order() - 1))
              .build());
      previousTick = pick.tick();
    }
    return rows;
  }

  private List<AbilityDraftPoolDomain> poolRows(
      long matchId, DraftFacts draft, List<AbilityDraftPickDomain> picks) {
    Map<Integer, AbilityDraftPickDomain> byPoolIndex = new HashMap<>();
    for (int i = 0; i < picks.size(); i++) {
      byPoolIndex.put(draft.picks().get(i).poolIndex(), picks.get(i));
    }
    List<AbilityDraftPoolDomain> rows = new ArrayList<>();
    for (DraftPoolEntry entry : draft.pool()) {
      AbilityDraftPickDomain pick = byPoolIndex.get(entry.poolIndex());
      rows.add(
          AbilityDraftPoolDomain.builder()
              .matchId(matchId)
              .poolIndex(entry.poolIndex())
              .abilityId((long) entry.abilityId())
              .abilityName(abilityNames.nameOf(entry.abilityId()).orElse(null))
              .ultimate(pick == null ? null : pick.getUltimate())
              .pickedBySlot(pick == null ? null : pick.getPlayerSlot())
              .pickOrder(pick == null ? null : pick.getPickOrder())
              .build());
    }
    return rows;
  }
}
