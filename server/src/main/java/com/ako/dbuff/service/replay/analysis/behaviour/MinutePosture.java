package com.ako.dbuff.service.replay.analysis.behaviour;

/** The posture of a team in one game minute (minute 12 is 12:00 to 12:59). */
public record MinutePosture(int minute, Posture posture) {}
