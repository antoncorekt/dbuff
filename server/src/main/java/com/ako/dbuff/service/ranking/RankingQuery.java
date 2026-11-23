package com.ako.dbuff.service.ranking;

import java.time.LocalDate;
import java.util.Set;

/** Empty {@code playerIds} means every stored player. */
public record RankingQuery(
    Set<Long> playerIds,
    LocalDate startDate,
    LocalDate endDate,
    Set<String> includedNames,
    Set<String> excludedNames,
    Set<String> heroNames,
    Set<String> gameModeNames,
    RankingSort sort,
    int minGames,
    Integer limit) {}
