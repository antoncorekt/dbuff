package com.ako.dbuff.service.instance;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.PlayersResponse;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.scheduler.InstanceSchedulerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing Dbuf instance configurations. This service handles registration, updates,
 * and retrieval of instance configurations. It is designed to be used by both REST API and Discord
 * bot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbufInstanceConfigService {

  /** Special game mode ID representing "all game modes" */
  public static final Long ALL_GAME_MODES_ID = -1L;

  /** Input value that maps to ALL_GAME_MODES_ID */
  public static final String ALL_GAME_MODES_INPUT = "all";

  private final DbufInstanceConfigRepository repository;
  private final PlayerRepo playerRepo;
  private final PlayersApi playersApi;
  private final ConstantsManagers constantsManagers;
  private final ObjectMapper objectMapper;
  private final InstanceSchedulerService instanceSchedulerService;

  /**
   * Registers a new Dbuf instance configuration.
   *
   * @param request the registration request
   * @return the created instance configuration
   * @throws IllegalArgumentException if validation fails
   * @throws IllegalStateException if a Discord channel already has a registered instance
   */
  @Transactional
  public DbufInstanceConfigResponse register(RegisterInstanceRequest request) {
    validateRegistrationRequest(request);

    // Check if Discord channel already has an instance
    if (request.getDiscordChannelId() != null
        && repository.existsByDiscordChannelId(request.getDiscordChannelId())) {
      throw new IllegalStateException(
          "Discord channel "
              + request.getDiscordChannelId()
              + " already has a registered instance");
    }

    if (CollectionUtils.isEmpty(request.getPlayerIds())) {
      throw new IllegalStateException("PlayersIds is empty");
    }

    // Fetch and create/update PlayerDomain entities
    Set<PlayerDomain> players = new HashSet<>();
    for (Long playerId : request.getPlayerIds()) {
      PlayerDomain player = getOrCreatePlayer(playerId);
      players.add(player);
    }

    // Validate and convert game modes (names to IDs)
    Set<Long> gameModeIds = validateAndConvertGameModes(request.getGameModes());

    LocalDateTime now = LocalDateTime.now();
    DbufInstanceConfigDomain domain =
        DbufInstanceConfigDomain.builder()
            .discordChannelId(request.getDiscordChannelId())
            .discordGuildId(request.getDiscordGuildId())
            .name(request.getName())
            .players(players)
            .gameModeIds(gameModeIds)
            .extraConfig(serializeExtraConfig(request.getExtraConfig()))
            .createdAt(now)
            .updatedAt(now)
            .active(true)
            .build();

    DbufInstanceConfigDomain saved = repository.save(domain);
    log.info(
        "Registered new instance configuration: id={}, players={}, gameModeIds={}",
        saved.getId(),
        saved.getPlayerIds(),
        saved.getGameModeIds());

    instanceSchedulerService.scheduleJobsForInstance(saved.getId());

    return toResponse(saved);
  }

  /**
   * Updates an existing instance configuration.
   *
   * @param instanceId the instance ID
   * @param request the update request
   * @return the updated instance configuration
   * @throws IllegalArgumentException if instance not found
   */
  @Transactional
  public DbufInstanceConfigResponse update(String instanceId, UpdateInstanceRequest request) {
    DbufInstanceConfigDomain domain =
        repository
            .findById(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));

    // Update players
    if (request.getAddPlayerIds() != null && !request.getAddPlayerIds().isEmpty()) {
      for (Long playerId : request.getAddPlayerIds()) {
        PlayerDomain player = getOrCreatePlayer(playerId);
        domain.getPlayers().add(player);
      }
    }
    if (request.getRemovePlayerIds() != null && !request.getRemovePlayerIds().isEmpty()) {
      domain.getPlayers().removeIf(player -> request.getRemovePlayerIds().contains(player.getId()));
    }

    // Update game modes (convert names to IDs)
    if (request.getAddGameModes() != null && !request.getAddGameModes().isEmpty()) {
      Set<Long> newModeIds = validateAndConvertGameModes(request.getAddGameModes());
      domain.getGameModeIds().addAll(newModeIds);
    }
    if (request.getRemoveGameModes() != null && !request.getRemoveGameModes().isEmpty()) {
      Set<Long> removeModeIds = validateAndConvertGameModes(request.getRemoveGameModes());
      domain.getGameModeIds().removeAll(removeModeIds);
    }

    // Update name if provided
    if (request.getName() != null) {
      domain.setName(request.getName());
    }

    // Update Discord channel ID if provided
    if (request.getDiscordChannelId() != null) {
      domain.setDiscordChannelId(request.getDiscordChannelId());
    }

    // Update Discord guild ID if provided
    if (request.getDiscordGuildId() != null) {
      domain.setDiscordGuildId(request.getDiscordGuildId());
    }

    // Update active status if provided
    if (request.getActive() != null) {
      domain.setActive(request.getActive());
    }

    domain.setUpdatedAt(LocalDateTime.now());
    DbufInstanceConfigDomain saved = repository.save(domain);
    log.info(
        "Updated instance configuration: id={}, players={}, gameModeIds={}",
        saved.getId(),
        saved.getPlayerIds(),
        saved.getGameModeIds());

    return toResponse(saved);
  }

  /**
   * Gets an instance configuration by ID.
   *
   * @param instanceId the instance ID
   * @return the instance configuration if found
   */
  public Optional<DbufInstanceConfigResponse> getById(String instanceId) {
    return repository.findById(instanceId).map(this::toResponse);
  }

  /**
   * Gets an instance configuration by Discord channel ID.
   *
   * @param discordChannelId the Discord channel ID
   * @return the instance configuration if found
   */
  public Optional<DbufInstanceConfigResponse> getByDiscordChannelId(String discordChannelId) {
    return repository.findByDiscordChannelId(discordChannelId).map(this::toResponse);
  }

  /**
   * Gets the domain entity by ID (for internal use).
   *
   * @param instanceId the instance ID
   * @return the domain entity if found
   */
  public Optional<DbufInstanceConfigDomain> getDomainById(String instanceId) {
    return repository.findById(instanceId);
  }

  /**
   * Gets all active instance configurations.
   *
   * @return list of active configurations
   */
  public List<DbufInstanceConfigResponse> getAllActive() {
    return repository.findByActiveTrue().stream().map(this::toResponse).toList();
  }

  /**
   * Gets all instance configurations that track a specific player.
   *
   * @param playerId the player ID
   * @return list of configurations tracking this player
   */
  public List<DbufInstanceConfigDomain> getActiveInstancesForPlayer(Long playerId) {
    return repository.findActiveByPlayerId(playerId);
  }

  /**
   * Gets all instance IDs that should process a match based on player participation.
   *
   * @param playerIds the player IDs in the match
   * @return set of instance IDs that should process this match
   */
  public Set<String> getInstanceIdsForPlayers(Set<Long> playerIds) {
    Set<String> instanceIds = new HashSet<>();
    for (Long playerId : playerIds) {
      repository
          .findActiveByPlayerId(playerId)
          .forEach(instance -> instanceIds.add(instance.getId()));
    }
    return instanceIds;
  }

  /**
   * Deactivates an instance configuration.
   *
   * @param instanceId the instance ID
   */
  @Transactional
  public void deactivate(String instanceId) {
    repository
        .findById(instanceId)
        .ifPresent(
            domain -> {
              domain.setActive(false);
              domain.setUpdatedAt(LocalDateTime.now());
              repository.save(domain);
              instanceSchedulerService.unscheduleJobsForInstance(instanceId);
              log.info("Deactivated instance configuration: id={}", instanceId);
            });
  }

  /**
   * Deletes an instance configuration.
   *
   * @param instanceId the instance ID
   */
  @Transactional
  public void delete(String instanceId) {
    instanceSchedulerService.unscheduleJobsForInstance(instanceId);
    repository.deleteById(instanceId);
    log.info("Deleted instance configuration: id={}", instanceId);
  }

  /**
   * Gets or creates a PlayerDomain entity for the given player ID. Fetches player info from
   * OpenDota API if not found locally.
   */
  private PlayerDomain getOrCreatePlayer(Long playerId) {
    // First try to find by ID
    Optional<PlayerDomain> existingPlayer =
        playerRepo.findAll().stream().filter(p -> playerId.equals(p.getId())).findFirst();

    if (existingPlayer.isPresent()) {
      return existingPlayer.get();
    }

    // Fetch from OpenDota API
    try {
      PlayersResponse playerResp = playersApi.getPlayersByAccountId(playerId);
      String playerName =
          playerResp.getProfile() != null
              ? playerResp.getProfile().getPersonaname()
              : "Player_" + playerId;

      log.info("Fetched player from API: id={}, name={}", playerId, playerName);

      // Check if player with this name exists
      Optional<PlayerDomain> byName = playerRepo.findByName(playerName);
      if (byName.isPresent()) {
        // Update the ID if needed
        PlayerDomain player = byName.get();
        if (player.getId() == null || !player.getId().equals(playerId)) {
          player.setId(playerId);
          return playerRepo.save(player);
        }
        return player;
      }

      // Create new player
      PlayerDomain newPlayer = PlayerDomain.builder().id(playerId).name(playerName).build();
      return playerRepo.save(newPlayer);

    } catch (ApiException e) {
      throw new RuntimeException("Can't find information about player_id " + playerId, e);
    }
  }

  /**
   * Validates and converts game mode names to IDs. Game modes can be provided as: - "all" - matches
   * all game modes (stored as -1) - names (e.g., "Ability Draft") - numeric IDs (e.g., "22")
   */
  private Set<Long> validateAndConvertGameModes(Set<String> gameModes) {
    if (gameModes == null || gameModes.isEmpty()) {
      return new HashSet<>();
    }

    Map<String, MatchTypeConstant> matchTypeMap = constantsManagers.getMatchTypeConstantMap();
    Set<Long> validModeIds = new HashSet<>();

    for (String gameMode : gameModes) {
      // Check for "all" special value — expand to all known game mode IDs
      if (ALL_GAME_MODES_INPUT.equalsIgnoreCase(gameMode)) {
        return matchTypeMap.keySet().stream().map(Long::parseLong).collect(Collectors.toSet());
      }

      // Check if it's already a numeric ID
      try {
        Long numericId = Long.parseLong(gameMode);
        if (matchTypeMap.containsKey(gameMode)) {
          validModeIds.add(numericId);
          continue;
        }
      } catch (NumberFormatException ignored) {
        // Not a numeric ID, try to find by name
      }

      // Try to find by name
      Optional<Map.Entry<String, MatchTypeConstant>> byName =
          matchTypeMap.entrySet().stream()
              .filter(
                  entry ->
                      entry.getValue().getName() != null
                          && entry.getValue().getName().equalsIgnoreCase(gameMode))
              .findFirst();

      if (byName.isPresent()) {
        validModeIds.add(Long.parseLong(byName.get().getKey()));
      } else {
        String availableGameModes =
            matchTypeMap.values().stream()
                .map(MatchTypeConstant::getName)
                .collect(Collectors.joining(", "));
        log.warn("Unknown game mode: {}. Available modes: all, {}", gameMode, availableGameModes);
        throw new IllegalArgumentException(
            "Unknown game mode: "
                + gameMode
                + ". Use 'all' for all game modes or specify a valid game mode name/ID. Available game modes: "
                + availableGameModes);
      }
    }

    return validModeIds;
  }

  /** Converts game mode IDs to names for display. */
  private Set<String> convertGameModeIdsToNames(Set<Long> gameModeIds) {
    if (gameModeIds == null || gameModeIds.isEmpty()) {
      return new HashSet<>();
    }

    // Check for "all" special value
    if (gameModeIds.contains(ALL_GAME_MODES_ID)) {
      return Set.of(ALL_GAME_MODES_INPUT);
    }

    Map<String, MatchTypeConstant> matchTypeMap = constantsManagers.getMatchTypeConstantMap();
    return gameModeIds.stream()
        .map(
            id -> {
              MatchTypeConstant constant = matchTypeMap.get(String.valueOf(id));
              return constant != null && constant.getName() != null
                  ? constant.getName()
                  : String.valueOf(id);
            })
        .collect(Collectors.toSet());
  }

  /**
   * Checks if the given game mode IDs represent "all game modes".
   *
   * @param gameModeIds the set of game mode IDs
   * @return true if it represents all game modes
   */
  public static boolean isAllGameModes(Set<Long> gameModeIds) {
    return gameModeIds != null && gameModeIds.contains(ALL_GAME_MODES_ID);
  }

  private void validateRegistrationRequest(RegisterInstanceRequest request) {
    if (request.getPlayerIds() == null || request.getPlayerIds().isEmpty()) {
      throw new IllegalArgumentException("At least one player ID is required");
    }
  }

  private String serializeExtraConfig(Map<String, Object> extraConfig) {
    if (extraConfig == null || extraConfig.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(extraConfig);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize extra config: {}", e.getMessage());
      return null;
    }
  }

  private Map<String, Object> deserializeExtraConfig(String extraConfig) {
    if (extraConfig == null || extraConfig.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(extraConfig, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("Failed to deserialize extra config: {}", e.getMessage());
      return null;
    }
  }

  private DbufInstanceConfigResponse toResponse(DbufInstanceConfigDomain domain) {
    // Convert PlayerDomain to PlayerInfo
    Set<PlayerInfo> playerInfos =
        domain.getPlayers().stream()
            .map(player -> PlayerInfo.builder().id(player.getId()).name(player.getName()).build())
            .collect(Collectors.toSet());

    return DbufInstanceConfigResponse.builder()
        .id(domain.getId())
        .discordChannelId(domain.getDiscordChannelId())
        .discordGuildId(domain.getDiscordGuildId())
        .name(domain.getName())
        .players(playerInfos)
        .gameModes(convertGameModeIdsToNames(domain.getGameModeIds()))
        .extraConfig(deserializeExtraConfig(domain.getExtraConfig()))
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .active(domain.getActive())
        .build();
  }
}
