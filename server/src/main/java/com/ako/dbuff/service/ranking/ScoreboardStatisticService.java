package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.ImageProcessor;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates scoreboard image analysis: detects the player names on an uploaded scoreboard,
 * excludes the focus group's own players ("us"), and computes {@link ExternalPlayerStatisticService
 * external player statistics} for each remaining opponent against the focus group.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreboardStatisticService {

  /** Placeholder produced by {@link ImageProcessor} for slots whose name OCR cannot read. */
  private static final String UNKNOWN_PLAYER = "UNKNOWN";

  private final ImageProcessor imageProcessor;
  private final ExternalPlayerStatisticService externalPlayerStatisticService;
  private final PlayerRepo playerRepo;
  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Detects scoreboard names and computes statistics using the focus group configured on the given
   * instance.
   *
   * @param instanceId the instance configuration ID
   * @param imageBytes the raw scoreboard image bytes
   * @return statistics for each detected opponent; an empty list if the instance is not found
   */
  @Transactional(readOnly = true)
  public List<ExternalPlayerStatisticResponse> getStatisticsForInstance(
      String instanceId, byte[] imageBytes) {
    Optional<DbufInstanceConfigDomain> instanceOpt =
        instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return List.of();
    }
    return getStatistics(instanceOpt.get().getPlayerIds(), imageBytes);
  }

  /**
   * Detects scoreboard names and computes statistics for each opponent against the given focus
   * group.
   *
   * @param focusPlayerIds the focus group (our own) account IDs
   * @param imageBytes the raw scoreboard image bytes
   * @return statistics for each detected opponent, excluding the focus group's own players
   */
  @Transactional(readOnly = true)
  public List<ExternalPlayerStatisticResponse> getStatistics(
      Collection<Long> focusPlayerIds, byte[] imageBytes) {

    // 1. Detect the player names on the scoreboard.
    List<String> detectedNames = imageProcessor.extractPlayerNames(imageBytes);
    log.info("Detected {} names from image: {}", detectedNames.size(), detectedNames);

    // 2. Resolve the focus group's own names so we can exclude ourselves.
    Set<String> focusNames = resolveFocusNames(focusPlayerIds);

    // 3. Keep only opponents' readable names: drop ourselves, the UNKNOWN placeholder and blanks.
    List<String> opponents =
        detectedNames.stream()
            .filter(name -> name != null && !name.isBlank())
            .filter(name -> !UNKNOWN_PLAYER.equalsIgnoreCase(name))
            .filter(name -> !focusNames.contains(normalize(name)))
            .distinct()
            .toList();

    log.info("Computing statistics for {} opponents: {}", opponents.size(), opponents);

    // 4. Compute statistics for each opponent against the focus group.
    return opponents.stream()
        .map(name -> externalPlayerStatisticService.getStatistics(focusPlayerIds, name))
        .toList();
  }

  /** Resolves the focus group's account IDs to their (normalized) player names. */
  private Set<String> resolveFocusNames(Collection<Long> focusPlayerIds) {
    if (focusPlayerIds == null || focusPlayerIds.isEmpty()) {
      return Set.of();
    }
    return playerRepo.findByAccountIds(focusPlayerIds).stream()
        .map(PlayerDomain::getName)
        .filter(name -> name != null && !name.isBlank())
        .map(this::normalize)
        .collect(Collectors.toSet());
  }

  private String normalize(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
