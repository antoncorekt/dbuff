package com.ako.dbuff.service.replay.analysis.skillstat;

/** The games a count covers: started in {@code [fromEpoch, toEpoch)}, the match itself left out. */
public record SkillWindow(long fromEpoch, long toEpoch, long excludedMatchId) {}
