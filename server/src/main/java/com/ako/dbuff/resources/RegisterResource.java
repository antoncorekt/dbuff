package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ErrorResponse;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST resource for managing Dbuf instance configurations. Provides endpoints for registration,
 * updates, and retrieval of instance configurations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
public class RegisterResource {

  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Registers a new Dbuf instance configuration.
   *
   * @param request the registration request containing player IDs, game modes, and optional Discord
   *     info
   * @return the created instance configuration with generated ID
   */
  @PostMapping
  public ResponseEntity<?> register(@RequestBody RegisterInstanceRequest request) {
    log.info(
        "Received registration request: players={}, gameModes={}, discordChannel={}",
        request.getPlayerIds(),
        request.getGameModes(),
        request.getDiscordChannelId());

    try {
      DbufInstanceConfigResponse response = instanceConfigService.register(request);
      log.info("Successfully registered instance: id={}", response.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid registration request: {}", e.getMessage());
      return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REQUEST", e.getMessage()));
    } catch (IllegalStateException e) {
      log.warn("Registration conflict: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    } catch (RuntimeException e) {
      log.error("Registration failed: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse.of("INTERNAL_ERROR", "Registration failed", e.getMessage()));
    }
  }

  /**
   * Gets an instance configuration by ID.
   *
   * @param id the instance ID
   * @return the instance configuration if found
   */
  @GetMapping("/{id}")
  public ResponseEntity<DbufInstanceConfigResponse> getById(@PathVariable String id) {
    return instanceConfigService
        .getById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Gets all active instance configurations.
   *
   * @return list of active configurations
   */
  @GetMapping
  public ResponseEntity<List<DbufInstanceConfigResponse>> getAllActive() {
    return ResponseEntity.ok(instanceConfigService.getAllActive());
  }

  /**
   * Updates an existing instance configuration. Supports adding/removing players and game modes.
   *
   * @param id the instance ID
   * @param request the update request
   * @return the updated instance configuration
   */
  @PatchMapping("/{id}")
  public ResponseEntity<?> update(
      @PathVariable String id, @RequestBody UpdateInstanceRequest request) {
    log.info(
        "Received update request for instance {}: addPlayers={}, removePlayers={}, addModes={}, removeModes={}, discordChannel={}",
        id,
        request.getAddPlayerIds(),
        request.getRemovePlayerIds(),
        request.getAddGameModes(),
        request.getRemoveGameModes(),
        request.getDiscordChannelId());

    try {
      DbufInstanceConfigResponse response = instanceConfigService.update(id, request);
      log.info("Successfully updated instance: id={}", response.getId());
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.warn("Update failed for instance {}: {}", id, e.getMessage());
      return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REQUEST", e.getMessage()));
    } catch (RuntimeException e) {
      log.error("Update failed for instance {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse.of("INTERNAL_ERROR", "Update failed", e.getMessage()));
    }
  }

  /**
   * Deactivates an instance configuration.
   *
   * @param id the instance ID
   * @return 204 No Content on success
   */
  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(@PathVariable String id) {
    log.info("Deactivating instance: {}", id);
    instanceConfigService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deletes an instance configuration.
   *
   * @param id the instance ID
   * @return 204 No Content on success
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    log.info("Deleting instance: {}", id);
    instanceConfigService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
