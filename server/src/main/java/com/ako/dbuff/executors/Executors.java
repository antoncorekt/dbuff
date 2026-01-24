package com.ako.dbuff.executors;

import com.ako.dbuff.context.ProcessContext;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Centralized executor configuration for virtual threads with ScopedValue support.
 * 
 * Virtual threads in Java 21 work excellently with ScopedValue for context propagation.
 * Unlike ThreadLocal, ScopedValue is designed for virtual threads and provides:
 * - Automatic inheritance to child virtual threads
 * - Better performance (no copying of values)
 * - Immutability guarantees within scope
 */
public class Executors {

  /**
   * Virtual thread executor for general processing tasks.
   * Each task runs in its own virtual thread.
   */
  public static final Executor PROCESSOR_VIRT_EXECUTOR = 
      java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Virtual thread executor service for tasks that need Future support.
   */
  public static final ExecutorService PROCESSOR_VIRT_EXECUTOR_SERVICE = 
      java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Executes a task in a virtual thread with the specified match ID in scope.
   * The match ID will be available via ProcessContext.MATCH_ID.get() within the task.
   *
   * @param matchId the match ID to set in scope
   * @param task the task to execute
   */
  public static void executeWithMatchId(Long matchId, Runnable task) {
    PROCESSOR_VIRT_EXECUTOR.execute(() -> 
        ProcessContext.runWithMatchId(matchId, task)
    );
  }

  /**
   * Executes a task in a virtual thread with the specified player ID in scope.
   * The player ID will be available via ProcessContext.PLAYER_ID.get() within the task.
   *
   * @param playerId the player ID to set in scope
   * @param task the task to execute
   */
  public static void executeWithPlayerId(Long playerId, Runnable task) {
    PROCESSOR_VIRT_EXECUTOR.execute(() -> 
        ProcessContext.runWithPlayerId(playerId, task)
    );
  }

  /**
   * Executes a task in a virtual thread with both match ID and player ID in scope.
   *
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope
   * @param task the task to execute
   */
  public static void executeWithContext(Long matchId, Long playerId, Runnable task) {
    PROCESSOR_VIRT_EXECUTOR.execute(() -> 
        ProcessContext.runWithContext(matchId, playerId, task)
    );
  }

  /**
   * Executes a task in a virtual thread with full context in scope.
   *
   * @param processType the process type (e.g., "MATCH_PROCESSING")
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope (can be null)
   * @param task the task to execute
   */
  public static void executeWithFullContext(String processType, Long matchId, Long playerId, Runnable task) {
    PROCESSOR_VIRT_EXECUTOR.execute(() -> 
        ProcessContext.runWithFullContext(processType, matchId, playerId, task)
    );
  }

  /**
   * Process type constants for consistent logging
   */
  public static final class ProcessTypes {
    public static final String MATCH_PROCESSING = "MATCH_PROCESSING";
    public static final String HISTORY_FETCH = "HISTORY_FETCH";
    public static final String PLAYER_STATS = "PLAYER_STATS";
    public static final String SCRAPING = "SCRAPING";
    
    private ProcessTypes() {}
  }
}
