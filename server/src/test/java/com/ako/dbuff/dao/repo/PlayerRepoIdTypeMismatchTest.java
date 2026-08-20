package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerDomain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterises the declared-ID-type mismatch on {@link PlayerRepo}.
 *
 * <p>{@code PlayerRepo extends JpaRepository<PlayerDomain, Long>} but {@link PlayerDomain}'s
 * {@code @Id} is the String {@code name}. So {@code existsById} is handed an OpenDota account ID
 * and compares it against a name column.
 *
 * <p>This matters because {@code PlayerConfiguration.setUpPlayers()} guards its seeding with {@code
 * existsById}. As these tests show, that guard never fires, so every startup re-saves every seeded
 * player — which silently clears any column the re-save does not set, including {@code
 * discordUserId}.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlayerRepoIdTypeMismatchTest {

  private static final Long ACCOUNT_ID = 204429164L;
  private static final String PLAYER_NAME = "Пастух лолей";

  @Autowired private PlayerRepo playerRepo;

  @Test
  void existsById_withAnAccountId_reportsFalseForAPlayerThatExists() {
    playerRepo.save(
        PlayerDomain.builder().id(ACCOUNT_ID).name(PLAYER_NAME).discordUserId("d-1").build());

    // Does not throw — it quietly answers the wrong question, which is why the
    // PlayerConfiguration seeding guard is ineffective rather than loudly broken.
    assertThat(playerRepo.existsById(ACCOUNT_ID)).isFalse();
    assertThat(playerRepo.findByName(PLAYER_NAME)).isPresent();
  }

  @Test
  void reSeedingWithoutDiscordUserId_clearsTheLink() {
    playerRepo.save(
        PlayerDomain.builder().id(ACCOUNT_ID).name(PLAYER_NAME).discordUserId("d-1").build());

    // Exactly what setUpPlayers() does on the next startup, because the guard above never fires.
    playerRepo.save(PlayerDomain.builder().id(ACCOUNT_ID).name(PLAYER_NAME).build());

    assertThat(playerRepo.findByName(PLAYER_NAME).orElseThrow().getDiscordUserId()).isNull();
  }
}
