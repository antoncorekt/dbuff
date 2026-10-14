package com.ako.dbuff.service.replay;

import java.util.List;

/**
 * The hero side of an Ability Draft: the hero pool and the hero picks in the order they happened.
 */
public record HeroDraftFacts(List<HeroDraftEntry> pool, List<DraftPick> picks) {}
