package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerDomain;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PlayerRepoDiscordLinkTest {

  @Autowired private PlayerRepo playerRepo;

  @BeforeEach
  void setUp() {
    playerRepo.save(
        PlayerDomain.builder().id(111L).name("Termit").discordUserId("discord-1").build());
    playerRepo.save(PlayerDomain.builder().id(222L).name("Unlinked").build());
  }

  @Test
  void findByDiscordUserId_returnsTheLinkedPlayer() {
    Optional<PlayerDomain> found = playerRepo.findByDiscordUserId("discord-1");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Termit");
    assertThat(found.get().getId()).isEqualTo(111L);
  }

  @Test
  void findByDiscordUserId_unknownId_isEmpty() {
    assertThat(playerRepo.findByDiscordUserId("nobody")).isEmpty();
  }

  @Test
  void discordUserId_isOptional() {
    // Looked up via findByName rather than findById: PlayerRepo declares its ID type as Long while
    // PlayerDomain's @Id is the String name, so findById is not usable here.
    Optional<PlayerDomain> unlinked = playerRepo.findByName("Unlinked");

    assertThat(unlinked).isPresent();
    assertThat(unlinked.get().getDiscordUserId()).isNull();
  }

  @Test
  void findAllByDiscordUserIdIsNotNull_returnsOnlyLinkedPlayers() {
    assertThat(playerRepo.findAllByDiscordUserIdIsNotNull())
        .extracting(PlayerDomain::getName)
        .containsExactly("Termit");
  }
}
