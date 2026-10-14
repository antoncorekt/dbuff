package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores the heroes of an Ability Draft pool: who took which and on which turn, and the rest. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeroDraftExtractor implements ReplayExtractor {
  private static final int RADIANT = 2;

  private final HeroDraftPickRepo repo;
  private final AbilityDraftProfile profile;

  @Override
  public String name() {
    return "hero-draft";
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == profile.gameModeId();
  }

  /**
   * A match with no hero draft, or one of an unexpected shape, stores nothing rather than a part.
   */
  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    HeroDraftFacts draft = facts.heroDraft();
    if (draft == null) {
      return;
    }
    if (draft.pool().size() != profile.heroPoolSize()
        || draft.picks().size() != profile.heroPicks()) {
      log.warn(
          "Match {} draft has {} heroes and {} hero picks, expected {} and {}; not stored",
          match.getId(),
          draft.pool().size(),
          draft.picks().size(),
          profile.heroPoolSize(),
          profile.heroPicks());
      return;
    }
    repo.saveAll(rows(match.getId(), facts, draft));
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private List<HeroDraftPickDomain> rows(long matchId, ReplayFacts facts, HeroDraftFacts draft) {
    Map<Integer, ReplayPlayerFact> players = facts.playersById();
    DraftTimeline.Turns turns = DraftTimeline.turns(facts.draft(), draft);
    Map<Integer, DraftPick> byPoolIndex = new HashMap<>();
    draft.picks().forEach(p -> byPoolIndex.put(p.poolIndex(), p));
    return draft.pool().stream()
        .map(
            entry -> {
              DraftPick pick = byPoolIndex.get(entry.poolIndex());
              HeroDraftPickDomain.HeroDraftPickDomainBuilder row =
                  HeroDraftPickDomain.builder()
                      .matchId(matchId)
                      .poolIndex(entry.poolIndex())
                      .heroId((long) entry.heroId());
              if (pick == null) {
                return row.build();
              }
              ReplayPlayerFact player = players.get(pick.replayPlayerId());
              if (player == null) {
                throw new IllegalStateException(
                    "Hero pick "
                        + pick.order()
                        + " belongs to unknown player "
                        + pick.replayPlayerId());
              }
              return row.playerSlot(player.playerSlot())
                  .team(player.teamNum() == RADIANT ? 0 : 1)
                  .turnNumber(turns.hero(pick.order()))
                  .pickTick(pick.tick())
                  .clockSeconds(DraftTimeline.clockSeconds(pick.tick(), facts.clockStartTick()))
                  .build();
            })
        .toList();
  }
}
