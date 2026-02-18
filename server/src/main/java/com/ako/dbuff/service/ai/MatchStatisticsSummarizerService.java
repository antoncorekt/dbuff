package com.ako.dbuff.service.ai;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import com.ako.dbuff.service.ai.model.MatchAnalysisRequest;
import com.ako.dbuff.service.ai.model.MatchWithPlayerStatistics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that summarizes processed matches by collecting player statistics and sending them to AI
 * for analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchStatisticsSummarizerService {

  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final AbilityRepo abilityRepo;
  private final ItemRepository itemRepository;
  private final MatchRepo matchRepo;
  private final MatchAnalysisAiService matchAnalysisAiService;

  /**
   * Summarizes and analyzes a list of processed matches. Collects player statistics for each match
   * and sends them to AI for comprehensive analysis.
   *
   * @param matches the list of processed matches to analyze
   * @param analyzePerMatch if true, each match is analyzed individually; if false, all matches are
   *     analyzed together
   * @return CompletableFuture containing the analysis result(s)
   */
  public CompletableFuture<List<MatchAnalysisDomain>> summarizeAndAnalyze(
      List<MatchDomain> matches, boolean analyzePerMatch) {
    return summarizeAndAnalyze(matches, analyzePerMatch, null, null);
  }

  /**
   * Summarizes and analyzes a list of processed matches with an optional custom prompt.
   * Collects player statistics for each match and sends them to AI for comprehensive analysis.
   *
   * @param matches the list of processed matches to analyze
   * @param analyzePerMatch if true, each match is analyzed individually; if false, all matches are
   *     analyzed together
   * @param customPrompt optional additional prompt to append to the context (can be null)
   * @return CompletableFuture containing the analysis result(s)
   */
  public CompletableFuture<List<MatchAnalysisDomain>> summarizeAndAnalyze(
      List<MatchDomain> matches, boolean analyzePerMatch, String customPrompt) {
    return summarizeAndAnalyze(matches, analyzePerMatch, customPrompt, null);
  }

  /**
   * Summarizes and analyzes a list of processed matches with optional custom prompt and field configuration.
   * Collects player statistics for each match and sends them to AI for comprehensive analysis.
   *
   * @param matches the list of processed matches to analyze
   * @param analyzePerMatch if true, each match is analyzed individually; if false, all matches are
   *     analyzed together
   * @param customPrompt optional additional prompt to append to the context (can be null)
   * @param fieldConfig optional configuration for which fields to include in the AI prompt (can be null)
   * @return CompletableFuture containing the analysis result(s)
   */
  public CompletableFuture<List<MatchAnalysisDomain>> summarizeAndAnalyze(
      List<MatchDomain> matches, boolean analyzePerMatch, String customPrompt,
      AiPromptFieldConfig fieldConfig) {
    return CompletableFuture.supplyAsync(
        () -> {
          List<MatchWithPlayerStatistics> matchesWithStats = collectMatchStatistics(matches);

          if (matchesWithStats.isEmpty()) {
            log.warn("No matches with statistics found for analysis");
            return List.of();
          }

          MatchAnalysisRequest request = buildAnalysisRequest(
              matchesWithStats, analyzePerMatch, customPrompt, fieldConfig);

          List<MatchAnalysisDomain> analyses;
          if (analyzePerMatch) {
            analyses = matchAnalysisAiService.analyzeMatchesIndividually(request);
          } else {
            MatchAnalysisDomain analysis = matchAnalysisAiService.analyzeMatches(request);
            analyses = List.of(analysis);
          }

          // Link analyses to matches
          linkAnalysesToMatches(matchesWithStats, analyses, analyzePerMatch);

          return analyses;
        },
        Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE);
  }

  /**
   * Summarizes and analyzes all matches together.
   *
   * @param matches the list of processed matches to analyze
   * @return CompletableFuture containing the single analysis result
   */
  public CompletableFuture<MatchAnalysisDomain> summarizeAndAnalyzeTogether(
      List<MatchDomain> matches) {
    return summarizeAndAnalyze(matches, false)
        .thenApply(
            analyses -> {
              if (analyses.isEmpty()) {
                return null;
              }
              return analyses.get(0);
            });
  }

  /**
   * Summarizes and analyzes each match individually.
   *
   * @param matches the list of processed matches to analyze
   * @return CompletableFuture containing the list of analysis results
   */
  public CompletableFuture<List<MatchAnalysisDomain>> summarizeAndAnalyzeIndividually(
      List<MatchDomain> matches) {
    return summarizeAndAnalyze(matches, true);
  }

  /**
   * Collects player statistics for each match.
   *
   * @param matches the list of matches
   * @return list of matches with their player statistics
   */
  private List<MatchWithPlayerStatistics> collectMatchStatistics(List<MatchDomain> matches) {
    List<MatchWithPlayerStatistics> result = new ArrayList<>();

    for (MatchDomain match : matches) {
      if (match == null || match.getId() == null) {
        continue;
      }

      List<PlayerMatchStatisticDomain> playerStats =
          playerGameStatisticRepo.findAllByMatchId(match.getId());

      List<AbilityDomain> allAbilities = abilityRepo.findAllByMatchId(match.getId());

      List<ItemDomain> allItems = itemRepository.findAllByMatchId(match.getId());

      if (playerStats.isEmpty()) {
        log.warn("No player statistics found for match {}", match.getId());
        continue;
      }

      MatchWithPlayerStatistics matchWithStats =
          MatchWithPlayerStatistics.builder()
              .match(match)
              .playerStatistics(playerStats)
              .abilities(allAbilities)
              .items(allItems)
              .build();

      if (!matchWithStats.hasCompletePlayerData()) {
        log.warn(
            "Match {} has incomplete player data: {} players instead of 10",
            match.getId(),
            matchWithStats.getPlayerCount());
      }

      result.add(matchWithStats);
    }

    return result;
  }

  /**
   * Builds the analysis request with context about tracked players.
   *
   * @param matchesWithStats the matches with their statistics
   * @param analyzePerMatch whether to analyze each match individually
   * @return the analysis request
   */
  private MatchAnalysisRequest buildAnalysisRequest(
      List<MatchWithPlayerStatistics> matchesWithStats, boolean analyzePerMatch) {
    return buildAnalysisRequest(matchesWithStats, analyzePerMatch, null, null);
  }

  /**
   * Builds the analysis request with context about tracked players and optional custom prompt.
   *
   * @param matchesWithStats the matches with their statistics
   * @param analyzePerMatch whether to analyze each match individually
   * @param customPrompt optional additional prompt to append to the context (can be null)
   * @return the analysis request
   */
  private MatchAnalysisRequest buildAnalysisRequest(
      List<MatchWithPlayerStatistics> matchesWithStats, boolean analyzePerMatch, String customPrompt) {
    return buildAnalysisRequest(matchesWithStats, analyzePerMatch, customPrompt, null);
  }

  /**
   * Builds the analysis request with context about tracked players, optional custom prompt,
   * and optional field configuration.
   *
   * @param matchesWithStats the matches with their statistics
   * @param analyzePerMatch whether to analyze each match individually
   * @param customPrompt optional additional prompt to append to the context (can be null)
   * @param fieldConfig optional configuration for which fields to include in the AI prompt (can be null)
   * @return the analysis request
   */
  private MatchAnalysisRequest buildAnalysisRequest(
      List<MatchWithPlayerStatistics> matchesWithStats, boolean analyzePerMatch,
      String customPrompt, AiPromptFieldConfig fieldConfig) {

    List<Long> focusPlayerIds = new ArrayList<>(PlayerConfiguration.DEFAULT_PLAYERS.keySet());
    List<String> focusPlayerNames = new ArrayList<>(PlayerConfiguration.DEFAULT_PLAYERS.values());

    String contextPrompt =
        buildContextPrompt(matchesWithStats.size(), focusPlayerNames, analyzePerMatch, customPrompt);

    return MatchAnalysisRequest.builder()
        .matchesWithStatistics(matchesWithStats)
        .contextPrompt(contextPrompt)
        .focusPlayerIds(focusPlayerIds)
        .focusPlayerNames(focusPlayerNames)
        .analyzePerMatch(analyzePerMatch)
        .fieldConfig(fieldConfig)
        .build();
  }

  /**
   * Builds the context prompt for the AI.
   *
   * @param matchCount number of matches being analyzed
   * @param focusPlayerNames names of players to focus on
   * @param analyzePerMatch whether analyzing per match or together
   * @return the context prompt
   */
  private String buildContextPrompt(
      int matchCount, List<String> focusPlayerNames, boolean analyzePerMatch) {
    return buildContextPrompt(matchCount, focusPlayerNames, analyzePerMatch, null);
  }

  /**
   * Builds the context prompt for the AI with optional custom prompt.
   *
   * @param matchCount number of matches being analyzed
   * @param focusPlayerNames names of players to focus on
   * @param analyzePerMatch whether analyzing per match or together
   * @param customPrompt optional additional prompt to append (can be null)
   * @return the context prompt
   */
  private String buildContextPrompt(
      int matchCount, List<String> focusPlayerNames, boolean analyzePerMatch, String customPrompt) {
    StringBuilder sb = new StringBuilder();

    sb.append("This is statistics from Dota 2 Ability Draft game matches. ");

    if (matchCount == 1) {
      sb.append("Analyzing a single match. ");
    } else if (analyzePerMatch) {
      sb.append("Analyzing ").append(matchCount).append(" matches individually. ");
    } else {
      sb.append("Analyzing ").append(matchCount).append(" matches together as a session. ");
    }

    sb.append("\n\nPlease focus on the following tracked players: ");
    sb.append(String.join(", ", focusPlayerNames));
    sb.append(".\n\n");

    sb.append("Key areas to highlight:\n");
    sb.append("- Win/loss patterns for tracked players\n");
    sb.append("- KDA performance and consistency\n");
    sb.append("- Farming efficiency (GPM, XPM, Last Hits)\n");
    sb.append("- Support contributions (wards, stacks)\n");
    sb.append("- Objective control (towers, Roshan)\n");
    sb.append("- Lane performance and efficiency\n");
    sb.append("- Hero choices and their effectiveness\n");
    sb.append("\n");
    sb.append("Provide actionable insights and recommendations for improvement.");

    // Append custom prompt if provided
    if (customPrompt != null && !customPrompt.isBlank()) {
      sb.append("\n\n--- Additional Instructions ---\n");
      sb.append(customPrompt);
    }

    return sb.toString();
  }

  /**
   * Links the analysis results to the corresponding matches in the database.
   *
   * @param matchesWithStats the matches that were analyzed
   * @param analyses the analysis results
   * @param analyzePerMatch whether each match was analyzed individually
   */
  @Transactional
  protected void linkAnalysesToMatches(
      List<MatchWithPlayerStatistics> matchesWithStats,
      List<MatchAnalysisDomain> analyses,
      boolean analyzePerMatch) {

    if (analyses.isEmpty()) {
      return;
    }

    if (analyzePerMatch) {
      // Each match gets its own analysis
      for (int i = 0; i < matchesWithStats.size() && i < analyses.size(); i++) {
        MatchDomain match = matchesWithStats.get(i).getMatch();
        MatchAnalysisDomain analysis = analyses.get(i);

        match.setAnalysis(analysis);
        matchRepo.save(match);

        log.info("Linked analysis {} to match {}", analysis.getId(), match.getId());
      }
    } else {
      // All matches share the same analysis
      MatchAnalysisDomain sharedAnalysis = analyses.get(0);

      for (MatchWithPlayerStatistics matchWithStats : matchesWithStats) {
        MatchDomain match = matchWithStats.getMatch();
        match.setAnalysis(sharedAnalysis);
        matchRepo.save(match);

        log.info("Linked shared analysis {} to match {}", sharedAnalysis.getId(), match.getId());
      }
    }
  }

  /**
   * Gets the analysis for a specific match if it exists.
   *
   * @param matchId the match ID
   * @return the analysis if found, null otherwise
   */
  public MatchAnalysisDomain getAnalysisForMatch(Long matchId) {
    return matchRepo
        .findById(matchId)
        .map(MatchDomain::getAnalysis)
        .orElse(null);
  }

  /**
   * Checks if a match has been analyzed.
   *
   * @param matchId the match ID
   * @return true if the match has an analysis
   */
  public boolean hasAnalysis(Long matchId) {
    return matchRepo
        .findById(matchId)
        .map(match -> match.getAnalysis() != null)
        .orElse(false);
  }

  /**
   * Gets statistics summary for logging purposes.
   *
   * @param matches the matches to summarize
   * @return a summary string
   */
  public String getStatisticsSummary(List<MatchDomain> matches) {
    List<MatchWithPlayerStatistics> matchesWithStats = collectMatchStatistics(matches);

    long totalPlayers =
        matchesWithStats.stream().mapToLong(MatchWithPlayerStatistics::getPlayerCount).sum();

    long trackedPlayerMatches =
        matchesWithStats.stream()
            .flatMap(m -> m.getPlayerStatistics().stream())
            .filter(p -> PlayerConfiguration.DEFAULT_PLAYERS.containsKey(p.getPlayerId()))
            .count();

    long wins =
        matchesWithStats.stream()
            .flatMap(m -> m.getPlayerStatistics().stream())
            .filter(p -> PlayerConfiguration.DEFAULT_PLAYERS.containsKey(p.getPlayerId()))
            .filter(p -> p.getWin() != null && p.getWin() == 1)
            .count();

    return String.format(
        "Matches: %d, Total players: %d, Tracked player appearances: %d, Wins: %d",
        matchesWithStats.size(), totalPlayers, trackedPlayerMatches, wins);
  }
}
