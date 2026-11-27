package com.ako.dbuff.dao.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Cross-match defense figures of one mode and checkpoint. A team's win comes from the match's
 * {@code radiant_win}; rows with a missing effective health or net worth are left out.
 */
@Repository
public class DefenseStatRepository {
  public static final int BUCKET_COUNT = 10;
  public static final double BUCKET_WIDTH = 0.05;
  public static final double BUCKET_FLOOR = -0.25;

  private static final String WIN =
      "case when (a.team = 'RADIANT' and m.radiant_win = true)"
          + " or (a.team = 'DIRE' and m.radiant_win = false) then 1 else 0 end";

  private static final String SELECT_ROWS_TEMPLATE =
      "select a.ehp_mix, b.ehp_mix, a.net_worth, b.net_worth, "
          + WIN
          + " from team_defense_point_domain a"
          + " join team_defense_point_domain b on b.match_id = a.match_id"
          + " and b.checkpoint = a.checkpoint and b.team <> a.team"
          + " join match_domain m on m.id = a.match_id"
          + " where a.checkpoint = :checkpoint and m.game_mode_id = :mode"
          + " and m.start_time >= :from and m.start_time < :to"
          + "%s"
          + " and a.ehp_mix is not null and b.ehp_mix is not null"
          + " and a.net_worth is not null and b.net_worth is not null";

  private static final String EXCLUDED = " and m.id <> :excluded";

  @PersistenceContext private EntityManager entityManager;

  /** Games and wins of one bucket; {@code from} is its lower edge, open for the first bucket. */
  public record Bucket(double from, double to, long games, long wins) {}

  /** Wins and games with and without an item of a kind by the checkpoint minute. */
  public record ItemSituation(long withGames, long withWins, long withoutGames, long withoutWins) {}

  /** Buckets of {@code ehpMixShare - netWorthShare}, 0.05 wide from -0.25 to 0.25. */
  public List<Bucket> buckets(
      int modeId, String checkpoint, long fromEpoch, long toEpoch, Long excludedMatchId) {
    return bucketize(rows(modeId, checkpoint, fromEpoch, toEpoch, excludedMatchId), true);
  }

  /** Buckets of {@code ehpMixShare} alone over the same edges shifted to 0.25..0.75. */
  public List<Bucket> rawBuckets(
      int modeId, String checkpoint, long fromEpoch, long toEpoch, Long excludedMatchId) {
    return bucketize(rows(modeId, checkpoint, fromEpoch, toEpoch, excludedMatchId), false);
  }

  public ItemSituation itemSituation(
      int modeId,
      String checkpoint,
      String kind,
      double minEnemyShare,
      long fromEpoch,
      long toEpoch,
      long excludedMatchId) {
    String share = "MAGIC".equals(kind) ? "a.enemy_mag_share" : "a.enemy_phys_share";
    Query query =
        entityManager.createNativeQuery(
            "select held, count(*), coalesce(sum("
                + WIN
                + "), 0) from (select a.team, a.match_id, exists (select 1 from"
                + " team_defense_item_domain i where i.match_id = a.match_id and i.team = a.team"
                + " and i.kind = :kind and i.at_second <= a.minute * 60) as held"
                + " from team_defense_point_domain a join match_domain m on m.id = a.match_id"
                + " where a.checkpoint = :checkpoint and m.game_mode_id = :mode"
                + " and m.start_time >= :from and m.start_time < :to and m.id <> :excluded and "
                + share
                + " >= :share) a join match_domain m on m.id = a.match_id group by held");
    query.setParameter("kind", kind);
    query.setParameter("checkpoint", checkpoint);
    query.setParameter("mode", modeId);
    query.setParameter("from", fromEpoch);
    query.setParameter("to", toEpoch);
    query.setParameter("excluded", excludedMatchId);
    query.setParameter("share", minEnemyShare);
    long withGames = 0;
    long withWins = 0;
    long withoutGames = 0;
    long withoutWins = 0;
    for (Object row : query.getResultList()) {
      Object[] cols = (Object[]) row;
      long games = ((Number) cols[1]).longValue();
      long wins = ((Number) cols[2]).longValue();
      if (Boolean.TRUE.equals(cols[0])) {
        withGames = games;
        withWins = wins;
      } else {
        withoutGames = games;
        withoutWins = wins;
      }
    }
    return new ItemSituation(withGames, withWins, withoutGames, withoutWins);
  }

  private List<Object[]> rows(
      int modeId, String checkpoint, long fromEpoch, long toEpoch, Long excludedMatchId) {
    Query query =
        entityManager.createNativeQuery(
            SELECT_ROWS_TEMPLATE.formatted(excludedMatchId == null ? "" : EXCLUDED));
    query.setParameter("checkpoint", checkpoint);
    query.setParameter("mode", modeId);
    query.setParameter("from", fromEpoch);
    query.setParameter("to", toEpoch);
    if (excludedMatchId != null) {
      query.setParameter("excluded", excludedMatchId);
    }
    return query.getResultList();
  }

  private static List<Bucket> bucketize(List<Object[]> rows, boolean adjusted) {
    long[] games = new long[BUCKET_COUNT];
    long[] wins = new long[BUCKET_COUNT];
    for (Object[] row : rows) {
      double ehp = ((Number) row[0]).doubleValue();
      double ehpEnemy = ((Number) row[1]).doubleValue();
      double worth = ((Number) row[2]).doubleValue();
      double worthEnemy = ((Number) row[3]).doubleValue();
      if (ehp + ehpEnemy <= 0 || (adjusted && worth + worthEnemy <= 0)) {
        continue;
      }
      double value = ehp / (ehp + ehpEnemy);
      double low = BUCKET_FLOOR;
      if (adjusted) {
        value -= worth / (worth + worthEnemy);
      } else {
        low = BUCKET_FLOOR + 0.5;
      }
      int index = index(value, low);
      games[index]++;
      wins[index] += ((Number) row[4]).longValue();
    }
    Bucket[] buckets = new Bucket[BUCKET_COUNT];
    double low = adjusted ? BUCKET_FLOOR : BUCKET_FLOOR + 0.5;
    for (int i = 0; i < BUCKET_COUNT; i++) {
      buckets[i] =
          new Bucket(
              round(low + i * BUCKET_WIDTH),
              round(low + (i + 1) * BUCKET_WIDTH),
              games[i],
              wins[i]);
    }
    return List.of(buckets);
  }

  private static int index(double value, double low) {
    double position = Math.floor(Math.round((value - low) / BUCKET_WIDTH * 1e6) / 1e6);
    return (int) Math.max(0, Math.min(BUCKET_COUNT - 1, position));
  }

  private static double round(double value) {
    return Math.round(value * 100) / 100.0;
  }
}
