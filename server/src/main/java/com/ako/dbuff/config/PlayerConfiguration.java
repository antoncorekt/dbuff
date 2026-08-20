package com.ako.dbuff.config;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.api.PlayersApi;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlayerConfiguration {

  @Autowired private PlayerRepo playerRepo;
  @Autowired private PlayersApi playersApi;

  public static final Map<Long, String> DEFAULT_PLAYERS;

  static {
    DEFAULT_PLAYERS = new HashMap<>();
    DEFAULT_PLAYERS.put(204429164L, "Пастух лолей");
    DEFAULT_PLAYERS.put(279195408L, "Доктор Сливси");
    DEFAULT_PLAYERS.put(201613150L, "Tigress");
    DEFAULT_PLAYERS.put(208611215L, "Лолец пастухов");
  }

  @PostConstruct
  public void setUpPlayers() {

    DEFAULT_PLAYERS.forEach(
        (id, name) -> {
          // Looked up by name, not existsById(id): PlayerRepo declares its ID type as Long while
          // PlayerDomain's @Id is the String name, so existsById(accountId) always answers false.
          // With the old guard every startup re-saved each player, clearing any column the re-save
          // did not set — including discordUserId, which would silently unlink everyone on restart.
          if (playerRepo.findByName(name).isEmpty()) {
            playerRepo.save(PlayerDomain.builder().id(id).name(name).build());
            log.info("Saved player [{}].", name);
          }
        });
  }
}
