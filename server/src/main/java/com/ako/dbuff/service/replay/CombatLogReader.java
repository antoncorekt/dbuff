package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What analyzers use to read a match's stored combat log. */
@Service
@RequiredArgsConstructor
public class CombatLogReader {
  private final ReplayCombatLogRepo repo;

  /** The events in the order they were written; empty when no replay was read for the match. */
  public List<CombatEvent> events(long matchId) {
    return repo.findByMatchId(matchId)
        .map(stored -> CombatLogCodec.decode(stored.getData()))
        .orElse(List.of());
  }
}
