package com.ako.dbuff.service.constant;

import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ConstantPrecacheStarter {

  private final List<ConstantRepo> constantRepoList;

  @PostConstruct
  public void init() {
    constantRepoList.forEach(
        constantRepo -> {
          log.info("Initializing constant repo: {}", constantRepo.getConstantId());

          Map map = constantRepo.getConstantMap();

          log.info(
              "Initialized constant repo successfully: {}, size: {}",
              constantRepo.getConstantId(),
              map.size());

          if (map.isEmpty()) {
            throw new RuntimeException("constant repo is empty");
          }
        });
  }
}
