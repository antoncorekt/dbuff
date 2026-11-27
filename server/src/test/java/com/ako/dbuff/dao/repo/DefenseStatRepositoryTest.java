package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.DefenseStatRepository.Bucket;
import com.ako.dbuff.dao.repo.DefenseStatRepository.ItemSituation;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:defensestat;MODE=PostgreSQL;NON_KEYWORDS=MINUTE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DefenseStatRepository.class)
class DefenseStatRepositoryTest {
  private static final long FROM = 1_000L;
  private static final long TO = 2_000L;

  @Autowired private DefenseStatRepository repository;
  @Autowired private EntityManager em;

  @Test
  void bucketEdgesAndOpenOuterBuckets() {
    // each game gives a row per team: 0.05 and -0.05 exactly, and +-0.40 for the open ends
    game(1L, 1L, true, 55, 45, 50, 50);
    game(2L, 1L, true, 10, 90, 50, 50);
    game(3L, 1L, true, 90, 10, 50, 50);

    List<Bucket> buckets = repository.buckets(1, "M20", FROM, TO, null);

    assertThat(buckets).hasSize(10);
    assertThat(buckets.get(0).from()).isEqualTo(-0.25);
    assertThat(buckets.get(9).to()).isEqualTo(0.25);
    assertThat(buckets.get(6).games()).isEqualTo(1);
    assertThat(buckets.get(4).games()).isEqualTo(1);
    assertThat(buckets.get(0).games()).isEqualTo(2);
    assertThat(buckets.get(9).games()).isEqualTo(2);
    assertThat(buckets.get(6).wins()).isEqualTo(1);
    assertThat(buckets.get(4).wins()).isZero();
    assertThat(buckets.get(9).wins()).isEqualTo(1);
  }

  @Test
  void rawBucketsUseTheEffectiveHealthShareAlone() {
    game(1L, 1L, true, 50, 50, 50, 50);

    List<Bucket> raw = repository.rawBuckets(1, "M20", FROM, TO, null);

    assertThat(raw.get(0).from()).isEqualTo(0.25);
    assertThat(raw.get(9).to()).isEqualTo(0.75);
    assertThat(raw.get(5).games()).isEqualTo(2);
  }

  @Test
  void theExcludedMatchTheOtherModeAndMissingFiguresAreLeftOut() {
    game(1L, 1L, true, 50, 50, 50, 50);
    game(2L, 1L, true, 50, 50, 50, 50);
    game(3L, 2L, true, 50, 50, 50, 50);
    game(4L, 1L, true, 50, 50, null, 50);

    long all = repository.buckets(1, "M20", FROM, TO, null).stream().mapToLong(Bucket::games).sum();
    long without =
        repository.buckets(1, "M20", FROM, TO, 2L).stream().mapToLong(Bucket::games).sum();

    assertThat(all).isEqualTo(4);
    assertThat(without).isEqualTo(2);
  }

  @Test
  void itemSituationSplitsByAnItemOfTheKindByTheMinute() {
    pointRow(1L, 1L, "RADIANT", 0.7, 100, 100);
    pointRow(1L, 1L, "DIRE", 0.1, 100, 100);
    item(1L, "RADIANT", "MAGIC", 1_100);
    matchRow(2L, 1L, false);
    pointRow(2L, 1L, "RADIANT", 0.7, 100, 100);
    item(2L, "RADIANT", "MAGIC", 1_300);
    matchRow(3L, 1L, true);
    pointRow(3L, 1L, "RADIANT", 0.7, 100, 100);
    matchRow(4L, 1L, true);
    pointRow(4L, 1L, "RADIANT", 0.2, 100, 100);

    ItemSituation result = repository.itemSituation(1, "M20", "MAGIC", 0.55, FROM, TO, 99L);

    assertThat(result).isEqualTo(new ItemSituation(1, 1, 2, 1));
    assertThat(repository.itemSituation(1, "M20", "MAGIC", 0.55, FROM, TO, 3L).withoutGames())
        .isEqualTo(1);
  }

  private void game(
      long id,
      long mode,
      boolean radiantWin,
      Integer radiantEhp,
      Integer direEhp,
      Integer radiantWorth,
      Integer direWorth) {
    matchRow(id, mode, radiantWin);
    em.persist(point(id, "RADIANT", radiantEhp, radiantWorth, "M20", 20, 0.0));
    em.persist(point(id, "DIRE", direEhp, direWorth, "M20", 20, 0.0));
    em.flush();
  }

  private void pointRow(long id, long mode, String team, double magShare, int ehp, int worth) {
    if (em.find(MatchDomain.class, id) == null) {
      matchRow(id, mode, true);
    }
    em.persist(point(id, team, ehp, worth, "M20", 20, magShare));
    em.flush();
  }

  private void matchRow(long id, long mode, boolean radiantWin) {
    em.persist(
        MatchDomain.builder()
            .id(id)
            .startTime(1_500L)
            .gameModeId(mode)
            .radiantWin(radiantWin)
            .build());
  }

  private void item(long matchId, String team, String kind, int atSecond) {
    em.persist(
        TeamDefenseItemDomain.builder()
            .matchId(matchId)
            .team(team)
            .playerSlot(0L)
            .item("pipe")
            .kind(kind)
            .atSecond(atSecond)
            .build());
    em.flush();
  }

  private static TeamDefensePointDomain point(
      long matchId,
      String team,
      Integer ehp,
      Integer worth,
      String checkpoint,
      int minute,
      double magShare) {
    return TeamDefensePointDomain.builder()
        .matchId(matchId)
        .team(team)
        .minute(minute)
        .ehpMix(ehp)
        .netWorth(worth)
        .checkpoint(checkpoint)
        .enemyMagShare(magShare)
        .enemyPhysShare(0.0)
        .build();
  }
}
