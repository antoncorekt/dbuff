package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.MatchAnalysisRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for deleting matches and all related data.
 *
 * <p>This service handles cascading deletion of:
 *
 * <ul>
 *   <li>Items associated with the match
 *   <li>Abilities associated with the match
 *   <li>Player match statistics
 *   <li>Match analysis (if not shared with other matches)
 *   <li>The match itself
 * </ul>
 */
@Slf4j
@Service
@AllArgsConstructor
public class MatchDeletionService {

  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final ItemRepository itemRepository;
  private final AbilityRepo abilityRepo;
  private final MatchAnalysisRepo matchAnalysisRepo;

  /**
   * Deletes a match and all related data.
   *
   * @param matchId the ID of the match to delete
   * @throws EntityNotFoundException if the match does not exist
   */
  @Transactional
  public void deleteMatch(Long matchId) {
    log.info("Starting deletion of match {} and all related data", matchId);

    MatchDomain match =
        matchRepo
            .findById(matchId)
            .orElseThrow(
                () -> new EntityNotFoundException("Match not found with id: " + matchId));

    // Delete items associated with the match
    log.debug("Deleting items for match {}", matchId);
    itemRepository.deleteByMatchId(matchId);

    // Delete abilities associated with the match
    log.debug("Deleting abilities for match {}", matchId);
    abilityRepo.deleteByMatchId(matchId);

    // Delete player statistics associated with the match
    log.debug("Deleting player statistics for match {}", matchId);
    playerGameStatisticRepo.deleteByMatchId(matchId);

    // Handle match analysis - only delete if not shared with other matches
    MatchAnalysisDomain analysis = match.getAnalysis();
    if (analysis != null) {
      log.debug("Checking if analysis {} can be deleted", analysis.getId());
      // Clear the reference first
      match.setAnalysis(null);
      matchRepo.save(match);

      // Check if any other matches reference this analysis
      long matchesWithSameAnalysis =
          matchRepo.findAll().stream()
              .filter(m -> m.getAnalysis() != null && m.getAnalysis().getId().equals(analysis.getId()))
              .count();

      if (matchesWithSameAnalysis == 0) {
        log.debug("Deleting orphaned analysis {}", analysis.getId());
        matchAnalysisRepo.deleteById(analysis.getId());
      } else {
        log.debug(
            "Analysis {} is still referenced by {} other matches, keeping it",
            analysis.getId(),
            matchesWithSameAnalysis);
      }
    }

    // Delete the match itself
    log.debug("Deleting match {}", matchId);
    matchRepo.deleteById(matchId);

    log.info("Successfully deleted match {} and all related data", matchId);
  }
}
