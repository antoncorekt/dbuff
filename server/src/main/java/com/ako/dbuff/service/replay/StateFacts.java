package com.ako.dbuff.service.replay;

import java.util.List;

/** What heroes gained, lost, cast and learned over the game, in time order. */
public record StateFacts(List<StateChange> changes) {}
