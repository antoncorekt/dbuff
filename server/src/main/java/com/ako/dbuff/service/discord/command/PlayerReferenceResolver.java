package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Turns the {@code player:} option into account IDs.
 *
 * <p>Accepts three forms per entry, because all three are things a user will actually type:
 *
 * <ul>
 *   <li>a Discord mention, {@code <@123456789>} — resolved via the {@code discordUserId} link
 *   <li>a player name from the channel's focus group, case-insensitively
 *   <li>a raw numeric account ID
 * </ul>
 *
 * <p>Unresolved entries are reported rather than dropped. Dropping one entry of a multi-player
 * request would silently answer a narrower question than the one asked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerReferenceResolver {

  /** Discord mentions arrive as {@code <@id>} or {@code <@!id>}. */
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  private final DbufInstanceConfigService instanceConfigService;
  private final PlayerRepo playerRepo;

  /**
   * The outcome of resolving player references.
   *
   * @param players resolved players in the order given, never null
   * @param unresolved entries that matched nothing, never null
   */
  public record Resolution(List<ResolvedPlayer> players, List<String> unresolved) {

    public boolean hasUnresolved() {
      return !unresolved.isEmpty();
    }

    public boolean isEmpty() {
      return players.isEmpty();
    }
  }

  /**
   * A resolved player.
   *
   * @param accountId the OpenDota account ID
   * @param name the player's display name
   */
  public record ResolvedPlayer(Long accountId, String name) {}

  /**
   * Resolves each entry against the focus group of {@code channelId}.
   *
   * @param channelId the parent text channel, used to find the instance
   * @param references the raw option entries
   * @return resolved players and anything that could not be resolved
   */
  public Resolution resolve(String channelId, List<String> references) {
    Map<String, PlayerInfo> focusGroup = focusGroupByName(channelId);

    List<ResolvedPlayer> resolved = new ArrayList<>();
    List<String> unresolved = new ArrayList<>();
    Set<Long> seen = new LinkedHashSet<>();

    for (String reference : references) {
      if (reference == null || reference.isBlank()) {
        continue;
      }
      String trimmed = reference.trim();

      Optional<ResolvedPlayer> player = resolveOne(trimmed, focusGroup);
      if (player.isEmpty()) {
        unresolved.add(trimmed);
      } else if (seen.add(player.get().accountId())) {
        resolved.add(player.get());
      }
    }
    return new Resolution(resolved, unresolved);
  }

  /**
   * The whole focus group of {@code channelId}, for a request that named no player.
   *
   * <p>Sorted by name rather than left in the config's {@code Set} order, which is a hash order and
   * therefore arbitrary. Without this the embeds would arrive in a different sequence on every
   * invocation, and a request trimmed to the player cap would drop a different player each time.
   *
   * @param channelId the parent text channel, used to find the instance
   * @return the tracked players with a known account ID, by name; empty when nothing is tracked
   */
  public List<ResolvedPlayer> focusGroup(String channelId) {
    return focusGroupByName(channelId).values().stream()
        .filter(player -> player.getId() != null)
        .map(player -> new ResolvedPlayer(player.getId(), player.getName()))
        .sorted(
            Comparator.comparing(
                ResolvedPlayer::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
        .toList();
  }

  /**
   * The closest focus-group player name to an unresolved entry, for "did you mean".
   *
   * @param channelId the parent text channel
   * @param unknown the entry that did not resolve
   * @return the nearest tracked player name, if close enough
   */
  public Optional<String> suggest(String channelId, String unknown) {
    // Uses the real names rather than the lower-cased lookup keys, so the suggestion echoed back to
    // the user is spelled the way the player is actually named.
    List<String> names =
        focusGroupByName(channelId).values().stream()
            .map(PlayerInfo::getName)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    return TextSimilarity.closest(unknown, names, MAX_SUGGESTION_DISTANCE);
  }

  private Optional<ResolvedPlayer> resolveOne(
      String reference, Map<String, PlayerInfo> focusGroup) {
    Optional<String> mentionedId = extractMentionId(reference);
    if (mentionedId.isPresent()) {
      return playerRepo
          .findByDiscordUserId(mentionedId.get())
          .filter(player -> player.getId() != null)
          .map(player -> new ResolvedPlayer(player.getId(), player.getName()));
    }

    PlayerInfo tracked = focusGroup.get(reference.toLowerCase());
    if (tracked != null && tracked.getId() != null) {
      return Optional.of(new ResolvedPlayer(tracked.getId(), tracked.getName()));
    }

    if (reference.chars().allMatch(Character::isDigit)) {
      Long accountId = Long.parseLong(reference);
      return Optional.of(
          new ResolvedPlayer(accountId, nameForAccountId(accountId).orElse(reference)));
    }
    return Optional.empty();
  }

  /** Extracts the snowflake from {@code <@123>} or {@code <@!123>}, or empty if not a mention. */
  private Optional<String> extractMentionId(String reference) {
    if (!reference.startsWith("<@") || !reference.endsWith(">")) {
      return Optional.empty();
    }
    String digits = reference.replaceAll("[^0-9]", "");
    return digits.isEmpty() ? Optional.empty() : Optional.of(digits);
  }

  private Optional<String> nameForAccountId(Long accountId) {
    return playerRepo.findByAccountIds(List.of(accountId)).stream()
        .map(PlayerDomain::getName)
        .findFirst();
  }

  /** Focus-group players keyed by lower-cased name. */
  private Map<String, PlayerInfo> focusGroupByName(String channelId) {
    Optional<DbufInstanceConfigResponse> config =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (config.isEmpty() || config.get().getPlayers() == null) {
      return Map.of();
    }

    Map<String, PlayerInfo> byName = new LinkedHashMap<>();
    for (PlayerInfo player : config.get().getPlayers()) {
      if (player.getName() != null) {
        byName.put(player.getName().toLowerCase(), player);
      }
    }
    return byName;
  }
}
