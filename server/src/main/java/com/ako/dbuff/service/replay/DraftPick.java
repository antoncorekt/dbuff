package com.ako.dbuff.service.replay;

/** One pick of the draft, in the order the replay recorded it. */
public record DraftPick(int order, int poolIndex, int replayPlayerId, int tick) {}
