package com.ako.dbuff.service.match;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.details.MatchParserHandler;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
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
   * Processes a list of matches asynchronously using virtual threads with StructuredTaskScope. Each
   * match is processed in its own virtual thread with the matchId available via ScopedValue for
   * logging and tracing.
   *
   * <p>Uses a semaphore to limit concurrent database operations and prevent exhausting the
   * connection pool.
   *
   * @param matchDomains the list of matches to process
   * @return a CompletableFuture containing the list of processed MatchDomain objects
   */
  public CompletableFuture<List<MatchDomain>> process(List<MatchDomain> matchDomains) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Fork a virtual thread for each match and collect subtasks
            List<StructuredTaskScope.Subtask<MatchDomain>> subtasks =
                matchDomains.stream()
                    .map(matchDomain -> scope.fork(() -> processMatchInternal(matchDomain)))
                    .toList();

            // Wait for all tasks to complete
            scope.join();

            // Check for any failures (will throw if any task failed)
            scope.throwIfFailed();

            // Collect results from all subtasks, filtering out nulls (already processed or errors)
            return subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(Objects::nonNull)
                .toList();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while processing matches");
            throw new RuntimeException("Match processing interrupted", e);
          } catch (Exception e) {
            log.error("Error during batch match processing: {}", e.getMessage(), e);
            throw new RuntimeException("Batch match processing failed", e);
          }
        },
        Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE);
  }

  /**
   * Processes a single match asynchronously using virtual threads with StructuredTaskScope. The
   * match is processed in a virtual thread with the matchId available via ScopedValue for logging
   * and tracing.
   *
   * @param matchDomain the match to process
   * @return a CompletableFuture containing the processed MatchDomain object
   */
  public CompletableFuture<MatchDomain> processMatch(MatchDomain matchDomain) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var subtask = scope.fork(() -> processMatchInternal(matchDomain));
            scope.join();
            scope.throwIfFailed();
            return subtask.get();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while processing match {}", matchDomain.getId());
            throw new RuntimeException("Match processing interrupted", e);
          } catch (Exception e) {
            log.error("Error processing match {}: {}", matchDomain.getId(), e.getMessage(), e);
            throw new RuntimeException("Match processing failed", e);
          }
        },
        Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE);
  }

  /**
   * Internal method that performs the actual match processing within a scoped context.
   *
   * @param matchDomain the match to process
   * @return the processed MatchDomain from matchParserHandler, or null if already processed or on
   *     error
   */
  private MatchDomain processMatchInternal(MatchDomain matchDomain) {
    Long gameId = matchDomain.getId();

    try {
      // Execute with full context for logging and tracing, returning the result
      return ProcessContext.callWithFullContext(
          Executors.ProcessTypes.MATCH_PROCESSING,
          gameId,
          null, // playerId will be set later when processing individual players
          () -> {
            // Acquire semaphore permit before processing (blocks if limit reached)
            matchProcessingSemaphore.acquire();
            try {
              log.info("{} Processing game {}", ProcessContext.getContextString(), gameId);
              MatchDomain result = matchParserHandler.handle(gameId);
              if (result == null) {
                log.info(
                    "{} Game {} was already processed or skipped",
                    ProcessContext.getContextString(),
                    gameId);
              } else {
                log.info(
                    "{} Finished processing game {}", ProcessContext.getContextString(), gameId);
              }
              return result;
            } finally {
              // Always release the permit
              matchProcessingSemaphore.release();
            }
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error(
          "{} Interrupted while waiting for semaphore for game {}",
          ProcessContext.getContextString(),
          gameId);
      handleProcessingError(matchDomain, gameId, e);
      return null;
    } catch (Exception e) {
      handleProcessingError(matchDomain, gameId, e);
      return null;
    }
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
