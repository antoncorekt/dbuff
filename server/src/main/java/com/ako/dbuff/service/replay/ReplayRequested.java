package com.ako.dbuff.service.replay;

/** A match's replay location was stored, so its replay can be fetched. */
public record ReplayRequested(long matchId) {}
