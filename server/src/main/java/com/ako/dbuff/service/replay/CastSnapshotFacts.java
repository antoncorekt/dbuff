package com.ako.dbuff.service.replay;

import java.util.List;

/** The frames recorded around the catalogued casts of a match, in the order they were taken. */
public record CastSnapshotFacts(List<CastSnapshot> snapshots) {}
