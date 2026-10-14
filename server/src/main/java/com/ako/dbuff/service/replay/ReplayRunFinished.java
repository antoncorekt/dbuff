package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.ReplayStatus;

/** A replay run ended with {@code status}; {@code retryLeft} says whether another try is due. */
public record ReplayRunFinished(long matchId, ReplayStatus status, boolean retryLeft) {}
