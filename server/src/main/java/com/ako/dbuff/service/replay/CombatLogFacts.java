package com.ako.dbuff.service.replay;

import java.util.List;

/** The hero-relevant part of the combat log, in the order it was written. */
public record CombatLogFacts(List<CombatEvent> events) {}
