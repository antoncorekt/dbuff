package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.details.MatchParserHandler;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchProcessorService {

  private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

  private final MatchParserHandler matchParserHandler;
  private final MatchRepo matchRepo;

  public void process(List<MatchDomain> matchDomains) {
    matchDomains.forEach(
        matchDomain -> {
          Long gameId = matchDomain.getId();
          executor.execute(
              () -> {
                try {
                  log.info("Processing game {}", gameId);
                  matchParserHandler.handle(gameId);
                  log.info("Finished processing game {}", gameId);
                } catch (Exception e) {
                  matchDomain.setError(e.getMessage());
                  matchRepo.save(matchDomain);
                  log.error(
                      "Failed to process game {} with exception: {}", gameId, e.getMessage(), e);
                }
              });
        });
  }
}
