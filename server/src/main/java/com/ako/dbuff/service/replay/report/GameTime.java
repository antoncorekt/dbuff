package com.ako.dbuff.service.replay.report;

/** Game seconds as people read them in the client. */
public final class GameTime {
  private GameTime() {}

  public static String clock(double seconds) {
    long whole = Math.round(seconds);
    String sign = whole < 0 ? "-" : "";
    long abs = Math.abs(whole);
    return String.format("%s%d:%02d", sign, abs / 60, abs % 60);
  }
}
