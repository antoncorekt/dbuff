package com.ako.dbuff.service.replay.analysis.activity;

/**
 * @param farmLookbackSeconds a creep last hit up to this long before a sample, or during it, counts
 *     the hero as farming then
 * @param travelUnitsPerSecond average speed between two samples above which the hero is travelling
 * @param jumpUnitsPerSecond a speed above this is a teleport, respawn or similar jump, not walking
 */
public record ActivityTuning(
    int farmLookbackSeconds, int travelUnitsPerSecond, int jumpUnitsPerSecond) {}
