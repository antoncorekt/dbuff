package com.ako.dbuff.context;

import java.util.concurrent.Callable;

/**
 * Process context using Java 21 ScopedValue for thread-safe context propagation. ScopedValues are
 * automatically inherited by virtual threads and provide better performance than ThreadLocal for
 * virtual thread workloads.
 */
public class ProcessContext {

  /** The type of process being executed (e.g., "MATCH_PROCESSING", "HISTORY_FETCH") */
  public static final ScopedValue<String> PROCESS_TYPE = ScopedValue.newInstance();

  /** The current match ID being processed - used for logging and tracing */
  public static final ScopedValue<Long> MATCH_ID = ScopedValue.newInstance();

  /** The current player ID being processed - used for logging and tracing */
  public static final ScopedValue<Long> PLAYER_ID = ScopedValue.newInstance();

  /** The current page number being processed - used for pagination logging */
  public static final ScopedValue<Integer> PAGE_NUM = ScopedValue.newInstance();

  /** User ID for request tracing (kept for backward compatibility) */
  public static final ScopedValue<String> USER_ID = ScopedValue.newInstance();

  /**
   * Runs a task with the specified match ID in scope. The match ID will be available via
   * MATCH_ID.get() within the task.
   *
   * @param matchId the match ID to set in scope
   * @param task the task to run
   */
  public static void runWithMatchId(Long matchId, Runnable task) {
    ScopedValue.runWhere(MATCH_ID, matchId, task);
  }

  /**
   * Runs a task with the specified player ID in scope. The player ID will be available via
   * PLAYER_ID.get() within the task.
   *
   * @param playerId the player ID to set in scope
   * @param task the task to run
   */
  public static void runWithPlayerId(Long playerId, Runnable task) {
    ScopedValue.runWhere(PLAYER_ID, playerId, task);
  }

  /**
   * Runs a task with the specified page number in scope. The page number will be available via
   * PAGE_NUM.get() within the task.
   *
   * @param pageNum the page number to set in scope
   * @param task the task to run
   */
  public static void runWithPageNum(Integer pageNum, Runnable task) {
    ScopedValue.runWhere(PAGE_NUM, pageNum, task);
  }

  /**
   * Calls a task with the specified page number in scope and returns the result.
   *
   * @param pageNum the page number to set in scope
   * @param task the task to call
   * @param <T> the return type
   * @return the result of the task
   * @throws Exception if the task throws an exception
   */
  public static <T> T callWithPageNum(Integer pageNum, Callable<T> task) throws Exception {
    return ScopedValue.callWhere(PAGE_NUM, pageNum, task);
  }

  /**
   * Runs a task with both match ID and player ID in scope.
   *
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope
   * @param task the task to run
   */
  public static void runWithContext(Long matchId, Long playerId, Runnable task) {
    ScopedValue.where(MATCH_ID, matchId).where(PLAYER_ID, playerId).run(task);
  }

  /**
   * Runs a task with full context (process type, match ID, player ID) in scope.
   *
   * @param processType the process type
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope
   * @param task the task to run
   */
  public static void runWithFullContext(
      String processType, Long matchId, Long playerId, Runnable task) {
    ScopedValue.where(PROCESS_TYPE, processType)
        .where(MATCH_ID, matchId)
        .where(PLAYER_ID, playerId)
        .run(task);
  }

  /**
   * Calls a task with full context (process type, match ID, player ID) in scope and returns the
   * result.
   *
   * @param processType the process type
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope (can be null)
   * @param task the task to call
   * @param <T> the return type
   * @return the result of the task
   * @throws Exception if the task throws an exception
   */
  public static <T> T callWithFullContext(
      String processType, Long matchId, Long playerId, Callable<T> task) throws Exception {
    return ScopedValue.where(PROCESS_TYPE, processType)
        .where(MATCH_ID, matchId)
        .where(PLAYER_ID, playerId)
        .call(task);
  }

  /**
   * Calls a task with the specified match ID in scope and returns the result.
   *
   * @param matchId the match ID to set in scope
   * @param task the task to call
   * @param <T> the return type
   * @return the result of the task
   * @throws Exception if the task throws an exception
   */
  public static <T> T callWithMatchId(Long matchId, Callable<T> task) throws Exception {
    return ScopedValue.callWhere(MATCH_ID, matchId, task);
  }

  /**
   * Calls a task with both match ID and player ID in scope and returns the result.
   *
   * @param matchId the match ID to set in scope
   * @param playerId the player ID to set in scope
   * @param task the task to call
   * @param <T> the return type
   * @return the result of the task
   * @throws Exception if the task throws an exception
   */
  public static <T> T callWithContext(Long matchId, Long playerId, Callable<T> task)
      throws Exception {
    return ScopedValue.where(MATCH_ID, matchId).where(PLAYER_ID, playerId).call(task);
  }

  /**
   * Gets the current match ID from the scoped context.
   *
   * @return the current match ID, or null if not set
   */
  public static Long getCurrentMatchId() {
    return MATCH_ID.isBound() ? MATCH_ID.get() : null;
  }

  /**
   * Gets the current player ID from the scoped context.
   *
   * @return the current player ID, or null if not set
   */
  public static Long getCurrentPlayerId() {
    return PLAYER_ID.isBound() ? PLAYER_ID.get() : null;
  }

  /**
   * Gets the current process type from the scoped context.
   *
   * @return the current process type, or null if not set
   */
  public static String getCurrentProcessType() {
    return PROCESS_TYPE.isBound() ? PROCESS_TYPE.get() : null;
  }

  /**
   * Gets the current page number from the scoped context.
   *
   * @return the current page number, or null if not set
   */
  public static Integer getCurrentPageNum() {
    return PAGE_NUM.isBound() ? PAGE_NUM.get() : null;
  }

  /**
   * Creates a formatted context string for logging purposes.
   *
   * @return a formatted string with current context values
   */
  public static String getContextString() {
    StringBuilder sb = new StringBuilder("[");
    if (PROCESS_TYPE.isBound()) {
      sb.append("process=").append(PROCESS_TYPE.get());
    }
    if (MATCH_ID.isBound()) {
      if (sb.length() > 1) sb.append(", ");
      sb.append("matchId=").append(MATCH_ID.get());
    }
    if (PLAYER_ID.isBound()) {
      if (sb.length() > 1) sb.append(", ");
      sb.append("playerId=").append(PLAYER_ID.get());
    }
    if (PAGE_NUM.isBound()) {
      if (sb.length() > 1) sb.append(", ");
      sb.append("page=").append(PAGE_NUM.get());
    }
    sb.append("]");
    return sb.toString();
  }
}
