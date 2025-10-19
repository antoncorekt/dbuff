package com.ako.dbuff.config;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlayerConfiguration {

  @Autowired private PlayerRepo playerRepo;

  public static final Map<Long, String> DEFAULT_PLAYERS =
      Map.of(
          204429164L, "Пастух лолей",
          279195408L, "Доктор Сливси",
          201613150L, "Tigress",
          208611215L, "Лолец пастухов");

  @PostConstruct
  public void setUpPlayers() {

    DEFAULT_PLAYERS.forEach(
        (id, name) -> {
          if (playerRepo.findById(id).isEmpty()) {
            playerRepo.save(PlayerDomain.builder().id(id).name(name).build());
            log.info("Saved player [{}].", name);
          }
        });
  }
}
