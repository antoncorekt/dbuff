package com.ako.dbuff.service.replay.analysis.activity;

/** What a hero was doing during a stretch of the game. */
public enum Activity {
  DEAD,
  TELEPORTING,
  IN_BASE,
  JUNGLE_FARM,
  LANE_FARM,
  /** In the enemy half of a lane, or at the enemy base, with no farming to explain it. */
  PUSHING,
  /** Moving fast with no farming to explain it. */
  TRAVELING,
  /** Anywhere else; the zone says where. */
  POSITIONED
}
