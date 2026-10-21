package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayCastSnapshotDomain;
import com.ako.dbuff.dao.repo.ReplayCastSnapshotRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores the cast snapshots of a match as one compressed blob. */
@Component
@RequiredArgsConstructor
public class ReplayCastSnapshotExtractor implements ReplayExtractor {
  private final ReplayCastSnapshotRepo repo;

  @Override
  public String name() {
    return "cast-snapshots";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    CastSnapshotFacts casts = facts.castSnapshots();
    if (casts == null || casts.snapshots().isEmpty()) {
      return;
    }
    repo.save(
        ReplayCastSnapshotDomain.builder()
            .matchId(match.getId())
            .codecVersion(CastSnapshotCodec.VERSION)
            .snapshotCount(casts.snapshots().size())
            .data(CastSnapshotCodec.encode(casts.snapshots()))
            .build());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
