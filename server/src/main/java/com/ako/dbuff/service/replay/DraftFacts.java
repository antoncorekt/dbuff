package com.ako.dbuff.service.replay;

import java.util.List;

/** The Ability Draft as the replay shows it: the whole pool and the picks in order. */
public record DraftFacts(List<DraftPoolEntry> pool, List<DraftPick> picks) {}
