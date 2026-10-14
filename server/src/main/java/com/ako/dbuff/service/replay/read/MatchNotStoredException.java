package com.ako.dbuff.service.replay.read;

public class MatchNotStoredException extends RuntimeException {
  public MatchNotStoredException(long matchId) {
    super("No replay run is stored for match " + matchId);
  }
}
