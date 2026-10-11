package com.ako.dbuff.service.replay;

/** Where Valve keeps the replay of one match. */
public record ReplaySource(long matchId, long cluster, long salt) {}
