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

//    DEFAULT_PLAYERS.keySet()
//            .forEach(
//                id -> {
//                  try {
//                    String name = playersApi.getPlayersByAccountId(id)
//                        .getProfile().getPersonaname();
//                    if (!DEFAULT_PLAYERS.get(id).equals(name) && name != null) {
//                      log.info("Looks like {} changed name to {}", DEFAULT_PLAYERS.get(id), name);
//                      DEFAULT_PLAYERS.put(id, name);
////                      if (playerRepo.existsById(id)) {
////                        playerRepo.deleteById(id);
////                      }
//                    }
//                  }catch (Exception e){
//                    log.error("error during getting player name {}",e.getMessage(), e);
//                  }
//                }
//            );

    DEFAULT_PLAYERS.forEach(
        (id, name) -> {
          if (!playerRepo.existsById(id)) {
            playerRepo.save(PlayerDomain.builder().id(id).name(name).build());
            log.info("Saved player [{}].", name);
          }
        });
  }
}
