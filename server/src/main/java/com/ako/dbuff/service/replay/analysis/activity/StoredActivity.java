package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads back the activity {@link ActivityAnalyzer} stored, so analyzers that use it read it alike.
 */
@Component
@RequiredArgsConstructor
public class StoredActivity {
  private final PlayerActivitySegmentRepo segmentRepo;

  /** The segments of each player in time order, by OpenDota player slot. */
  public Map<Long, List<ActivitySegment>> bySlot(long matchId) {
    Map<Long, List<ActivitySegment>> bySlot = new HashMap<>();
    for (PlayerActivitySegmentDomain s : segmentRepo.findByMatchId(matchId)) {
      bySlot.computeIfAbsent(s.getPlayerSlot(), k -> new ArrayList<>()).add(ActivitySegment.of(s));
    }
    bySlot
        .values()
        .forEach(
            list -> list.sort(java.util.Comparator.comparingInt(ActivitySegment::startSeconds)));
    return bySlot;
  }
}
