package com.ako.dbuff.service.replay;

/** A cumulative per-player counter the replay keeps, sampled once a game minute. */
public enum MinuteStat {
  HERO_KILL_GOLD(Source.TEAM_DATA, "m_iHeroKillGold"),
  CREEP_KILL_GOLD(Source.TEAM_DATA, "m_iCreepKillGold"),
  NEUTRAL_KILL_GOLD(Source.TEAM_DATA, "m_iNeutralKillGold"),
  BOUNTY_GOLD(Source.TEAM_DATA, "m_iBountyGold"),
  ROSHAN_GOLD(Source.TEAM_DATA, "m_iRoshanGold"),
  BUILDING_GOLD(Source.TEAM_DATA, "m_iBuildingGold"),
  COURIER_GOLD(Source.TEAM_DATA, "m_iCourierGold"),
  COMEBACK_GOLD(Source.TEAM_DATA, "m_iComebackGold"),
  INCOME_GOLD(Source.TEAM_DATA, "m_iIncomeGold"),
  SHARED_GOLD(Source.TEAM_DATA, "m_iSharedGold"),
  WARD_KILL_GOLD(Source.TEAM_DATA, "m_iWardKillGold"),
  CREEP_DENY_GOLD(Source.TEAM_DATA, "m_iCreepDenyGold"),
  OTHER_GOLD(Source.TEAM_DATA, "m_iOtherGold"),
  RELIABLE_GOLD(Source.TEAM_DATA, "m_iReliableGold"),
  UNRELIABLE_GOLD(Source.TEAM_DATA, "m_iUnreliableGold"),
  NEARBY_CREEP_DEATHS(Source.TEAM_DATA, "m_iNearbyCreepDeathCount"),
  MISS_COUNT(Source.TEAM_DATA, "m_iMissCount"),
  CLAIMED_MISS_COUNT(Source.TEAM_DATA, "m_iClaimedMissCount"),
  OBS_PLACED(Source.TEAM_DATA, "m_iObserverWardsPlaced"),
  SENT_PLACED(Source.TEAM_DATA, "m_iSentryWardsPlaced"),
  WARDS_DESTROYED(Source.TEAM_DATA, "m_iWardsDestroyed"),
  CREEPS_STACKED(Source.TEAM_DATA, "m_iCreepsStacked"),
  CAMPS_STACKED(Source.TEAM_DATA, "m_iCampsStacked"),
  GOLD_LOST_TO_DEATH(Source.TEAM_DATA, "m_iGoldLostToDeath"),
  GOLD_SPENT_ON_BUYBACKS(Source.TEAM_DATA, "m_iGoldSpentOnBuybacks"),
  COURIER_KILLS(Source.TEAM_DATA, "m_iCourierKills"),
  KILLS(Source.PLAYER_TEAM_DATA, "m_iKills"),
  DEATHS(Source.PLAYER_TEAM_DATA, "m_iDeaths"),
  ASSISTS(Source.PLAYER_TEAM_DATA, "m_iAssists"),
  BOUNTY_RUNES(Source.PLAYER_TEAM_DATA, "m_iBountyRunes"),
  POWER_RUNES(Source.PLAYER_TEAM_DATA, "m_iPowerRunes"),
  WATER_RUNES(Source.PLAYER_TEAM_DATA, "m_iWaterRunes");

  /** Where the counter lives: the per-team data entity or the shared player resource. */
  public enum Source {
    TEAM_DATA,
    PLAYER_TEAM_DATA
  }

  /** Value stored when the replay has no such field. */
  public static final long MISSING = -1;

  private final Source source;
  private final String property;

  MinuteStat(Source source, String property) {
    this.source = source;
    this.property = property;
  }

  public Source source() {
    return source;
  }

  public String property() {
    return property;
  }
}
