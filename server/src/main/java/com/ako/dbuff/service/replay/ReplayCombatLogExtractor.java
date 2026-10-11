package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores the hero-relevant combat log as one compressed blob. */
@Component
@RequiredArgsConstructor
public class ReplayCombatLogExtractor implements ReplayExtractor {
  private final ReplayCombatLogRepo repo;

  @Override
  public String name() {
    return "combat-log";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    CombatLogFacts log = facts.combatLog();
    if (log == null || log.events().isEmpty()) {
      return;
    }
    repo.save(
        ReplayCombatLogDomain.builder()
            .matchId(match.getId())
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(log.events().size())
            .data(CombatLogCodec.encode(log.events()))
            .build());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
