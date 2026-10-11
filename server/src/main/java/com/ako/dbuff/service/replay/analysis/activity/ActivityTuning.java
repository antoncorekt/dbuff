package com.ako.dbuff.service.replay.analysis.activity;

/**
 * @param farmWindowSeconds a creep last hit this close to a sample counts the hero as farming then
 * @param travelUnitsPerSecond average speed between two samples above which the hero is travelling
 */
public record ActivityTuning(int farmWindowSeconds, int travelUnitsPerSecond) {
  public static ActivityTuning defaults() {
    return new ActivityTuning(10, 200);
  }
}
