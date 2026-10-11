package com.ako.dbuff.service.replay.clarity;

/**
 * Turns the replay's ticks into game ticks, which stand still while the game is paused. The combat
 * log is stamped in game time, so anything sampled on raw ticks drifts away from it by the length
 * of every pause.
 */
final class GameClock {
  private long pausedTicks;
  private long pauseStartTick = -1;

  /** Called with the pause flag whenever the game rules change. */
  void paused(boolean paused, long tick) {
    if (paused && pauseStartTick < 0) {
      pauseStartTick = tick;
    } else if (!paused && pauseStartTick >= 0) {
      pausedTicks += tick - pauseStartTick;
      pauseStartTick = -1;
    }
  }

  /** The tick counted without the time the game spent paused. */
  long gameTick(long tick) {
    long running = pauseStartTick >= 0 ? tick - pauseStartTick : 0;
    return tick - pausedTicks - running;
  }
}
