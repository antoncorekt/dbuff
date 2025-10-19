package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/players")
public class PlayerResources {

  private final PlayerRepo playerRepo;

  @GetMapping("/")
  public Page<PlayerDomain> parseMatch(Pageable pageable) {
    return playerRepo.findAll(pageable);
  }
}
