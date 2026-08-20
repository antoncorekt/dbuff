package com.ako.dbuff.dao.model;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code @Index} declarations added for the ranking queries actually reach the
 * generated schema.
 *
 * <p>Worth a test because {@code @Index(columnList = ...)} takes <em>entity property</em> names and
 * relies on the physical naming strategy to translate them. A typo or an unmapped property is
 * silently ignored by Hibernate — the index simply never appears, and the only symptom is a slow
 * query in production. Nothing else in the suite would notice.
 *
 * <p>This runs against H2, so it proves the mapping is well-formed and the names resolve. It does
 * not prove Postgres created them; {@code ddl-auto=update} handles indexes less reliably than
 * columns, so confirm with {@code \di idx_*} after deploying.
 */
@DataJpaTest
@ActiveProfiles("test")
class RankingIndexDeclarationTest {

  @Autowired private EntityManager entityManager;

  @Test
  void everyDeclaredRankingIndexExists() {
    assertThat(indexNames())
        .contains(
            "IDX_ITEM_DOMAIN_PLAYER_MATCH_SLOT",
            "IDX_ITEM_DOMAIN_ITEM_ID",
            "IDX_ABILITY_DOMAIN_PLAYER_MATCH_SLOT",
            "IDX_ABILITY_DOMAIN_ABILITY_ID",
            "IDX_PLAYER_MATCH_STAT_PLAYER_MATCH",
            "IDX_PLAYER_MATCH_STAT_PLAYER_HERO",
            "IDX_PLAYER_MATCH_STAT_MATCH_ID",
            "IDX_MATCH_DOMAIN_START_LOCAL_DATE");
  }

  /**
   * The hero filter added to the ranking queries is the reason this index exists, and it is the one
   * whose {@code columnList} spans two properties that both need snake_case translation.
   */
  @Test
  void theHeroFilterIndexCoversPlayerThenHero() {
    assertThat(columnsOf("IDX_PLAYER_MATCH_STAT_PLAYER_HERO"))
        .containsExactly("PLAYER_ID", "HERO_ID");
  }

  @Test
  void theMatchDateIndexResolvesTheCamelCaseProperty() {
    assertThat(columnsOf("IDX_MATCH_DOMAIN_START_LOCAL_DATE")).containsExactly("START_LOCAL_DATE");
  }

  @SuppressWarnings("unchecked")
  private List<String> indexNames() {
    return entityManager
        .createNativeQuery("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES")
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  private List<String> columnsOf(String indexName) {
    return entityManager
        .createNativeQuery(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                + "WHERE INDEX_NAME = :name ORDER BY ORDINAL_POSITION")
        .setParameter("name", indexName)
        .getResultList();
  }
}
