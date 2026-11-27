package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.DefenseStatRepository;
import com.ako.dbuff.dao.repo.DefenseStatRepository.Bucket;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the stored defense rows of a match and the cross-match buckets of the last 12 months. */
@Service
@RequiredArgsConstructor
public class DefenseStatReader {
  public static final Set<String> CHECKPOINTS = Set.of("M10", "M20", "M30", "END");
  private static final int WINDOW_MONTHS = 12;

  private final TeamDefensePointRepo pointRepo;
  private final TeamDefenseItemRepo itemRepo;
  private final DefenseStatRepository statRepository;

  /** Armor and resistance in the points are estimates; the flag says so to every client. */
  public record MatchDefense(
      Long matchId,
      boolean armorEstimated,
      List<TeamDefensePointDomain> points,
      List<TeamDefenseItemDomain> items) {}

  public record Stats(
      int mode,
      String checkpoint,
      int windowMonths,
      List<Bucket> buckets,
      List<Bucket> rawBuckets) {}

  @Transactional(readOnly = true)
  public MatchDefense match(long matchId) {
    List<TeamDefensePointDomain> points =
        pointRepo.findByMatchId(matchId).stream()
            .sorted(
                Comparator.comparing(
                        TeamDefensePointDomain::getMinute,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                        TeamDefensePointDomain::getTeam,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    List<TeamDefenseItemDomain> items =
        itemRepo.findByMatchId(matchId).stream()
            .sorted(
                Comparator.comparing(
                        TeamDefenseItemDomain::getAtSecond,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                        TeamDefenseItemDomain::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new MatchDefense(matchId, true, points, items);
  }

  @Transactional(readOnly = true)
  public Stats stats(int modeId, String checkpoint) {
    Instant now = Instant.now();
    long to = now.getEpochSecond();
    long from = now.atZone(ZoneOffset.UTC).minusMonths(WINDOW_MONTHS).toEpochSecond();
    return new Stats(
        modeId,
        checkpoint,
        WINDOW_MONTHS,
        statRepository.buckets(modeId, checkpoint, from, to, null),
        statRepository.rawBuckets(modeId, checkpoint, from, to, null));
  }
}
