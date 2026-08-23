package com.ako.dbuff.resources.model;

import java.time.LocalDate;

/**
 * A match the statistics were computed over, for tracing a number back to its games.
 *
 * @param matchId the match ID, which is also its Dotabuff/OpenDota identifier
 * @param startDate the day the match was played, null when the scraper never recorded one
 */
public record MatchReference(Long matchId, LocalDate startDate) {}
