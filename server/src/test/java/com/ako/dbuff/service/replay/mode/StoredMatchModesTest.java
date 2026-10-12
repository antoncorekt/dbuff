package com.ako.dbuff.service.replay.mode;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class StoredMatchModesTest {

  @Autowired private EntityManager entityManager;

  @Test
  void activeFor_resolvesTheModeOfAStoredMatch_andIsEmptyForAnInactiveOrMissingOne() {
    entityManager.persist(MatchDomain.builder().id(1L).gameModeId(22L).build());
    entityManager.persist(MatchDomain.builder().id(2L).gameModeId(3L).build());
    entityManager.flush();
    StoredMatchModes modes =
        new StoredMatchModes(entityManager, TestModes.registry(new ReplayProcessingProperties()));

    assertThat(modes.activeFor(1L)).containsSame(TestModes.ALL_PICK);
    assertThat(modes.activeFor(2L)).isEmpty();
    assertThat(modes.activeFor(99L)).isEmpty();
  }
}
