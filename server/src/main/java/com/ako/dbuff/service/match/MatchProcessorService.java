package com.ako.dbuff.service.match;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.details.MatchParserHandler;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchProcessorService {

  private final MatchParserHandler matchParserHandler;
  private final MatchRepo matchRepo;
  private final Semaphore matchProcessingSemaphore;

  /**
   * Processes a list of matches asynchronously using virtual threads. Each match is processed in
   * its own virtual thread with the matchId available via ScopedValue for logging and tracing.
   *
   * <p>Uses a semaphore to limit concurrent database operations and prevent exhausting the
   * connection pool.
   *
   * @param matchDomains the list of matches to process
   */
  public void process(List<MatchDomain> matchDomains) {
    matchDomains.forEach(this::processMatch);
  }

  private void processMatch(MatchDomain matchDomain) {
    Long gameId = matchDomain.getId();

    // Execute in virtual thread with matchId in scope for logging
    Executors.executeWithFullContext(
        Executors.ProcessTypes.MATCH_PROCESSING,
        gameId,
        null, // playerId will be set later when processing individual players
        () -> {
          try {
            // Acquire semaphore permit before processing (blocks if limit reached)
            matchProcessingSemaphore.acquire();
            try {
              log.info("{} Processing game {}", ProcessContext.getContextString(), gameId);
              matchParserHandler.handle(gameId);
              log.info("{} Finished processing game {}", ProcessContext.getContextString(), gameId);
            } finally {
              // Always release the permit
              matchProcessingSemaphore.release();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(
                "{} Interrupted while waiting for semaphore for game {}",
                ProcessContext.getContextString(),
                gameId);
          } catch (Exception e) {
            handleProcessingError(matchDomain, gameId, e);
          }
        });
  }

  private void handleProcessingError(MatchDomain matchDomain, Long gameId, Exception e) {
    matchDomain.setError(e.getMessage());
    matchRepo.save(matchDomain);
    log.error(
        "{} Failed to process game {} with exception: {}",
        ProcessContext.getContextString(),
        gameId,
        e.getMessage(),
        e);
  }
}
