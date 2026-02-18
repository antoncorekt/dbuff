package com.ako.dbuff.service.match;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.ai.MatchStatisticsSummarizerService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class LastMatchesProcessorService {

  DotaApiLastMatchesService dotaApiLastMatchesService;
  MatchProcessorService matchProcessorService;
  MatchRepo matchRepo;
  MatchStatisticsSummarizerService matchStatisticsSummarizerService;

  public Set<Long> processLastMatches() {
    return processLastMatches(true, false);
  }

  /**
   * Processes the last matches for all configured players.
   *
   * @param enableAiAnalysis if true, AI analysis will be performed after processing
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatches(boolean enableAiAnalysis) {
    return processLastMatches(enableAiAnalysis, false);
  }

  /**
   * Processes the last matches for all configured players.
   *
   * @param enableAiAnalysis if true, AI analysis will be performed after processing
   * @param analyzePerMatch if true, each match is analyzed individually by AI; if false, all
   *     matches are analyzed together as a session (only used if enableAiAnalysis is true)
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatches(boolean enableAiAnalysis, boolean analyzePerMatch) {

    Set<Long> matchesToFetch =
        PlayerConfiguration.DEFAULT_PLAYERS.keySet().stream()
            .map(playerId -> dotaApiLastMatchesService.fetchLastMatches(playerId))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

    log.info("Matches to fetch: {}", matchesToFetch);

    List<MatchDomain> matchDomains =
        matchesToFetch.stream()
            .map(
                id ->
                    matchRepo
                        .findById(id)
                        .orElseGet(() -> matchRepo.save(MatchDomain.builder().id(id).build())))
            .toList();

    matchProcessorService
        .process(matchDomains)
        .thenAccept(
            listMatches -> {
              log.info("Handled matches {}", listMatches.size());

              if (listMatches.isEmpty()) {
                log.info("No new matches to analyze");
                return;
              }

              // Log statistics summary
              String summary = matchStatisticsSummarizerService.getStatisticsSummary(listMatches);
              log.info("Match statistics summary: {}", summary);

              // Only perform AI analysis if enabled
              if (!enableAiAnalysis) {
                log.info("AI analysis is disabled, skipping");
                return;
              }

              // Send processed matches to AI for analysis
              matchStatisticsSummarizerService
                  .summarizeAndAnalyze(listMatches, analyzePerMatch)
                  .thenAccept(
                      analyses -> {
                        log.info(
                            "AI analysis completed. Generated {} analysis report(s)",
                            analyses.size());
                        for (MatchAnalysisDomain analysis : analyses) {
                          if (analysis.getSuccess()) {
                            log.info(
                                "Analysis {} for matches [{}]: {} tokens used",
                                analysis.getId(),
                                analysis.getMatchIds(),
                                analysis.getTokensUsed());
                          } else {
                            log.warn(
                                "Analysis {} failed for matches [{}]: {}",
                                analysis.getId(),
                                analysis.getMatchIds(),
                                analysis.getErrorMessage());
                          }
                        }
                      })
                  .exceptionally(
                      ex -> {
                        log.error("Failed to analyze matches with AI: {}", ex.getMessage(), ex);
                        return null;
                      });
            });

    return matchesToFetch;
  }

  /**
   * Processes the last matches and analyzes them together as a session.
   *
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatchesWithSessionAnalysis() {
    return processLastMatches(true, false);
  }

  /**
   * Processes the last matches and analyzes each match individually.
   *
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatchesWithIndividualAnalysis() {
    return processLastMatches(true, true);
  }

  /**
   * Processes the last matches without AI analysis.
   *
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatchesWithoutAnalysis() {
    return processLastMatches(false, false);
  }
}
