package com.ako.dbuff.service.replay.analysis.fight;

public enum FightType {
  /** Three or more hero deaths, as OpenDota counts a teamfight. */
  TEAMFIGHT,
  /** One or two hero deaths. */
  SKIRMISH,
  /** No death, but both sides dealt real damage. */
  ENGAGEMENT
}
