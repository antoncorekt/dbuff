package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.details.MatchParserHandler;
import com.ako.dbuff.service.match.LastMatchesProcessorService;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/matches")
public class MatchResources {

  private final MatchParserHandler matchParserHandler;
  private final LastMatchesProcessorService lastMatchesProcessorService;

  @PostMapping("/{id}/parse")
  public MatchDomain parseMatch(@PathVariable String id) {
    return matchParserHandler.handle(Long.parseLong(id));
  }

  @PostMapping("/processLast")
  public Set<Long> processLastMatches() {
    return lastMatchesProcessorService.processLastMatches();
  }
}
