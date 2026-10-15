package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillCounts;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillPair;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatCalculator;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatMath;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatProperties;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillWindow;
import com.ako.dbuff.service.replay.smoke.LocalDatabase;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The count queries against a real PostgreSQL ({@code SMOKE_PG_URL}, localhost only), on a small
 * history whose answers are worked out by hand. Ids are far from any real or fixture data, and the
 * transaction rolls back.
 */
@Tag("replay")
@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=${SMOKE_PG_URL:jdbc:postgresql://localhost:5544/dbuff}",
      "spring.datasource.username=postgres",
      "spring.datasource.password=password",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=update",
      "spring.jpa.show-sql=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SkillCountRepository.class, SkillStatCalculator.class, SkillStatProperties.class})
@ExtendWith(LocalDatabase.class)
class SkillCountRepositoryPostgresTest {
  private static final long NOW = 2_000_000_000L;
  private static final long DAY = 86_400L;
  private static final long ABILITY_DRAFT = 18;
  private static final long ALL_PICK = 22;
  private static final long BASE = 9_100_000_000L;
  private static final long HERO = 9001;
  private static final long OTHER_HERO = 9002;
  private static final long A = 9101;
  private static final long B = 9102;
  private static final long C = 9103;
  private static final long ANALYSED_MATCH = BASE + 7;
  private static final SkillWindow WINDOW = new SkillWindow(NOW - 365 * DAY, NOW, ANALYSED_MATCH);

  @Autowired private EntityManager em;
  @Autowired private SkillCountRepository counts;
  @Autowired private SkillStatCalculator calculator;
  @Autowired private SkillPickStatRepo statRepo;

  @BeforeEach
  void history() {
    match(1, ABILITY_DRAFT, NOW - 10 * DAY);
    player(1, 0, HERO, 1, A, B);
    player(1, 128, OTHER_HERO, 0, A);
    match(2, ABILITY_DRAFT, NOW - 20 * DAY);
    player(2, 0, HERO, 0, A, B);
    match(3, ABILITY_DRAFT, NOW - 30 * DAY);
    player(3, 0, HERO, 1, A, C);
    match(4, ALL_PICK, NOW - 10 * DAY);
    player(4, 0, HERO, 1, A, B);
    match(5, ABILITY_DRAFT, NOW - 400 * DAY);
    player(5, 0, HERO, 1, A, B);
    match(6, ABILITY_DRAFT, NOW + 10 * DAY);
    player(6, 0, HERO, 1, A, B);
    match(7, ABILITY_DRAFT, NOW);
    player(7, 0, HERO, 1, A, B, C);
    em.flush();
  }

  private void match(int n, long mode, long start) {
    em.persist(MatchDomain.builder().id(BASE + n).gameModeId(mode).startTime(start).build());
  }

  private void player(int n, long slot, long hero, long win, long... skills) {
    PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
    stat.setMatchId(BASE + n);
    stat.setPlayerSlot(slot);
    stat.setPlayerId(-1L);
    stat.setHeroId(hero);
    stat.setWin(win);
    em.persist(stat);
    for (long skill : skills) {
      em.persist(
          AbilityDomain.builder()
              .matchId(BASE + n)
              .playerSlot(slot)
              .abilityId(skill)
              .playerId(-1L)
              .build());
    }
  }

  @Test
  void theHeroCountsOnlyAbilityDraftGamesInTheWindowWithoutTheMatchItself() {
    assertThat(counts.hero(HERO, WINDOW)).isEqualTo(new SkillCounts(3, 2));
    assertThat(counts.hero(OTHER_HERO, WINDOW)).isEqualTo(new SkillCounts(1, 0));
    assertThat(counts.hero(9999, WINDOW)).isEqualTo(SkillCounts.NONE);
  }

  @Test
  void eachSkillIsCountedForEveryPlayerThatHeldIt() {
    assertThat(counts.skills(List.of(A, B, C), WINDOW))
        .isEqualTo(
            Map.of(
                A, new SkillCounts(4, 2),
                B, new SkillCounts(2, 1),
                C, new SkillCounts(1, 1)));
  }

  @Test
  void aHeroWithASkillCountsOnlyThatHerosPlayers() {
    assertThat(counts.heroWithSkills(HERO, List.of(A, B, C), WINDOW))
        .isEqualTo(
            Map.of(
                A, new SkillCounts(3, 2),
                B, new SkillCounts(2, 1),
                C, new SkillCounts(1, 1)));
  }

  @Test
  void aPairIsCountedWhenOnePlayerHeldBothAndAPairNeverSeenIsAbsent() {
    assertThat(counts.pairs(List.of(A, B, C), WINDOW))
        .isEqualTo(
            Map.of(
                SkillPair.of(A, B), new SkillCounts(2, 1),
                SkillPair.of(A, C), new SkillCounts(1, 1)));
  }

  @Test
  void aNarrowerWindowDropsTheOlderGames() {
    SkillWindow lastMonth = new SkillWindow(NOW - 15 * DAY, NOW, ANALYSED_MATCH);

    assertThat(counts.skills(List.of(A), lastMonth)).isEqualTo(Map.of(A, new SkillCounts(2, 1)));
    assertThat(counts.pairs(List.of(A, B), lastMonth))
        .isEqualTo(Map.of(SkillPair.of(A, B), new SkillCounts(1, 1)));
  }

  @Test
  void theCalculatorTurnsThemIntoRowsWithTheGamesWithoutTheOtherSkill() {
    List<SkillPickStatDomain> rows =
        calculator.forPlayer(ANALYSED_MATCH, 0, HERO, List.of(A, B, C), WINDOW, 1);

    assertThat(rows).hasSize(1 + 3 + 3 + 3);
    SkillPickStatDomain ab =
        rows.stream()
            .filter(r -> r.getKind() == SkillStatKind.SKILL_PAIR && r.getSecondId() == B)
            .filter(r -> r.getFirstId() == A)
            .findFirst()
            .orElseThrow();
    assertThat(ab.getGames()).isEqualTo(2);
    assertThat(ab.getFirstGamesWithout()).isEqualTo(2);
    assertThat(ab.getFirstWinsWithout()).isEqualTo(1);
    assertThat(ab.getSecondGamesWithout()).isZero();
    assertThat(SkillStatMath.liftPoints(ab)).as("B has no game without A").isEmpty();
  }

  @Test
  void talentsAndGenericBonusesAreNotCountedAsSkills() {
    match(8, ABILITY_DRAFT, NOW - 5 * DAY);
    player(8, 0, HERO, 1, A);
    em.persist(talent(8, 0, 9201, "special_bonus_unique_sniper_5"));
    em.persist(talent(8, 0, 9202, "ad_special_bonus_gold_lvl20_r"));
    em.persist(talent(8, 0, 9203, "ability_attribute_bonus"));
    em.flush();

    assertThat(counts.skills(List.of(A, 9201L, 9202L, 9203L), WINDOW).keySet()).containsExactly(A);
    assertThat(counts.pairs(List.of(A, B, C, 9201L, 9202L, 9203L), WINDOW).keySet())
        .containsExactlyInAnyOrder(SkillPair.of(A, B), SkillPair.of(A, C));
  }

  @Test
  void aHeroIsReadFromTheMatchThatStartedLastNotTheHighestId() {
    em.persist(heroRow(1, 3));
    em.persist(heroRow(2, 5));
    em.flush();

    assertThat(
            statRepo.latestMatchesOfHero(
                HERO, org.springframework.data.domain.PageRequest.of(0, 1)))
        .containsExactly(BASE + 1);
  }

  private static AbilityDomain talent(int n, long slot, long id, String name) {
    return AbilityDomain.builder()
        .matchId(BASE + n)
        .playerSlot(slot)
        .abilityId(id)
        .playerId(-1L)
        .name(name)
        .build();
  }

  private static SkillPickStatDomain heroRow(int n, long games) {
    return SkillPickStatDomain.builder()
        .matchId(BASE + n)
        .playerSlot(0L)
        .kind(SkillStatKind.HERO)
        .firstId(HERO)
        .games(games)
        .wins(0L)
        .build();
  }
}
