package com.ako.dbuff.service.replay.mode;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Finds the active mode profile of a match that is already stored. */
@Component
@RequiredArgsConstructor
public class StoredMatchModes {
  private final EntityManager entityManager;
  private final GameModeRegistry registry;

  /** Empty when the match is not stored, has no mode, or its mode is not active. */
  public Optional<GameModeProfile> activeFor(long matchId) {
    List<Long> ids =
        entityManager
            .createQuery("select m.gameModeId from MatchDomain m where m.id = :id", Long.class)
            .setParameter("id", matchId)
            .getResultList();
    return ids.stream().findFirst().flatMap(registry::activeFor);
  }
}
