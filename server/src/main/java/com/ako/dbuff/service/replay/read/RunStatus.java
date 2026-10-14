package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.ReplayStatus;

/** How the replay run of a match ended. */
public record RunStatus(
    Long matchId, ReplayStatus status, Integer attempts, String error, String mapVersion) {}
