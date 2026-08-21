package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.discord.command.TextSimilarity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves user-supplied game mode names to the numeric IDs stored on {@code
 * MatchDomain.gameModeId}.
 *
 * <p>The dictionary is the OpenDota match-type constant map, the same one {@code /dbuff} validates
 * its tracked modes against — so a mode this bot can be configured to collect is always a mode
 * statistics can be filtered by. On top of it sit two aliases for the modes anyone actually asks
 * for: {@value #ABILITY_DRAFT} and {@value #ALL_PICK}. The second one matters, because the mode
 * Dota calls "All Pick" in the client is named {@code game_mode_all_draft} in the constants, and
 * nobody types that.
 *
 * <p>Unknown names are returned, never dropped. An empty ID set means "no filter" to the
 * repositories, so discarding a typo would silently widen a mode-scoped question into an every-mode
 * answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameModeResolver {

  /** Alias for Ability Draft, and the default when a caller names no mode at all. */
  public static final String ABILITY_DRAFT = "ability_draft";

  /** Alias for the mode the client calls All Pick, stored as {@code game_mode_all_draft}. */
  public static final String ALL_PICK = "all_pick";

  /** Token that clears the filter entirely. */
  public static final String ALL = "all";

  /** Constant-name prefix every mode carries, e.g. {@code game_mode_ability_draft}. */
  private static final String MODE_PREFIX = "game_mode_";

  /** Beyond this edit distance a "did you mean" is noise rather than help. */
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  /** Alias to the constant name it stands for. */
  private static final Map<String, String> ALIASES =
      Map.of(
          ABILITY_DRAFT, MODE_PREFIX + ABILITY_DRAFT,
          ALL_PICK, MODE_PREFIX + "all_draft");

  private final ConstantsManagers constantsManagers;

  /**
   * Resolves game mode tokens, defaulting to {@link #ABILITY_DRAFT} when none are given.
   *
   * <p>This is the entry point for the Discord commands: an omitted {@code game_mode:} option means
   * Ability Draft, which is what this bot is for.
   *
   * @param tokens what the user typed, in any accepted spelling; null or empty for the default
   * @return the resolved selection
   */
  public GameModeSelection resolveOrDefault(Set<String> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      return resolve(Set.of(ABILITY_DRAFT));
    }
    return resolve(tokens);
  }

  /**
   * Resolves game mode tokens, treating "none given" as "every mode".
   *
   * <p>Deliberately different from {@link #resolveOrDefault}: the services must not invent a mode
   * filter a caller did not ask for, or the REST API would quietly answer a narrower question than
   * the one it was sent.
   *
   * @param tokens what the caller supplied; null or empty for no filter
   * @return the resolved selection
   */
  public GameModeSelection resolve(Set<String> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      return GameModeSelection.allModes();
    }

    Set<String> canonicalNames = new LinkedHashSet<>();
    Set<Long> ids = new LinkedHashSet<>();
    Set<String> unresolved = new LinkedHashSet<>();
    List<String> labels = new ArrayList<>();

    for (String token : tokens) {
      if (token == null || token.isBlank()) {
        continue;
      }
      String trimmed = token.trim();
      if (ALL.equalsIgnoreCase(trimmed)) {
        // One "all" among other modes still means all of them: it is the widest request made.
        return GameModeSelection.allModes();
      }

      Optional<Mode> mode = lookup(trimmed);
      if (mode.isEmpty()) {
        unresolved.add(trimmed);
        continue;
      }
      canonicalNames.add(mode.get().name());
      ids.add(mode.get().id());
      labels.add(mode.get().display());
    }

    if (!unresolved.isEmpty()) {
      log.debug("Unresolved game mode names: {}", unresolved);
    }
    if (canonicalNames.isEmpty() && unresolved.isEmpty()) {
      return GameModeSelection.allModes();
    }

    return new GameModeSelection(
        canonicalNames,
        ids,
        unresolved,
        labels.isEmpty() ? GameModeSelection.ALL_MODES_LABEL : String.join(", ", labels));
  }

  /**
   * The nearest known mode name, for a "did you mean" on a typo.
   *
   * @param unknown what the user typed
   * @return the suggested display name, if one is close enough
   */
  public Optional<String> suggest(String unknown) {
    if (unknown == null || unknown.isBlank()) {
      return Optional.empty();
    }
    String needle = normalize(unknown);

    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (Map.Entry<String, String> candidate : displayToCanonicalName().entrySet()) {
      for (String matchable : List.of(candidate.getKey(), candidate.getValue())) {
        int distance = TextSimilarity.editDistance(needle, normalize(matchable));
        if (distance < bestDistance) {
          bestDistance = distance;
          best = candidate.getKey();
        }
      }
    }
    return bestDistance <= MAX_SUGGESTION_DISTANCE ? Optional.ofNullable(best) : Optional.empty();
  }

  /**
   * Every mode a picker should offer, display name to the value to submit.
   *
   * <p>The two aliases come first and submit their alias rather than the constant name, so the
   * value a user sees in the option box stays readable — and so {@code All Pick} does not submit
   * {@code game_mode_all_draft}, which reads like a different mode.
   *
   * @return display name to submitted value, iteration-ordered with the common modes first
   */
  public Map<String, String> displayToSubmittedValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    candidates.put("Ability Draft", ABILITY_DRAFT);
    candidates.put("All Pick", ALL_PICK);
    candidates.put(GameModeSelection.ALL_MODES_LABEL, ALL);

    displayToCanonicalName()
        .forEach((display, canonical) -> candidates.putIfAbsent(display, canonical));
    return candidates;
  }

  /** Display name to constant name, over the whole dictionary. */
  private Map<String, String> displayToCanonicalName() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (Map.Entry<String, MatchTypeConstant> entry :
        constantsManagers.getMatchTypeConstantMap().entrySet()) {
      MatchTypeConstant mode = entry.getValue();
      String name = mode.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      candidates.put(prettyName(name, idOf(entry)), name);
    }
    return candidates;
  }

  /** A mode resolved from the dictionary. */
  private record Mode(String name, Long id, String display) {}

  /**
   * Finds the mode a token names.
   *
   * <p>Accepts an alias, the constant name, the display name, and the raw numeric ID, because all
   * four reach this method in practice: autocomplete submits an alias or constant name, a user
   * typing freehand uses the display name, and the older text command took the number.
   *
   * <p>An alias is resolved <em>exclusively</em>. {@value #ALL_PICK} would otherwise also match the
   * constant {@code game_mode_all_pick} by the prefixing rule below — the unranked legacy mode,
   * which is not the mode the client labels All Pick and would quietly report different games.
   */
  private Optional<Mode> lookup(String token) {
    String normalized = normalize(token);
    String aliased = ALIASES.get(normalized);
    if (aliased != null) {
      return findByPredicate(name -> name.equalsIgnoreCase(aliased), id -> false);
    }

    String prefixed = normalized.startsWith(MODE_PREFIX) ? normalized : MODE_PREFIX + normalized;
    return findByPredicate(
        name -> name.equalsIgnoreCase(normalized) || name.equalsIgnoreCase(prefixed),
        id -> id.equals(normalized));
  }

  /** Scans the dictionary for the first mode whose name or ID satisfies the given tests. */
  private Optional<Mode> findByPredicate(
      Predicate<String> nameMatches, Predicate<String> idMatches) {

    for (Map.Entry<String, MatchTypeConstant> entry :
        constantsManagers.getMatchTypeConstantMap().entrySet()) {
      MatchTypeConstant mode = entry.getValue();
      String name = mode.getName();
      String id = idOf(entry);
      if (name == null || id == null) {
        continue;
      }
      if (!nameMatches.test(name) && !idMatches.test(id)) {
        continue;
      }
      Long numericId = parseId(id);
      if (numericId == null) {
        continue;
      }
      return Optional.of(new Mode(name, numericId, prettyName(name, id)));
    }
    return Optional.empty();
  }

  /** Turns {@code game_mode_ability_draft} into {@code Ability Draft}, falling back to the ID. */
  public static String prettyName(String rawName, String id) {
    if (rawName == null || rawName.isBlank()) {
      return "Mode " + id;
    }
    String stripped = rawName.replace(MODE_PREFIX, "").replace('_', ' ').trim();
    if (stripped.isEmpty()) {
      return "Mode " + id;
    }
    StringBuilder pretty = new StringBuilder(stripped.length());
    boolean capitalize = true;
    for (char c : stripped.toCharArray()) {
      pretty.append(capitalize ? Character.toUpperCase(c) : c);
      capitalize = c == ' ';
    }
    return pretty.toString();
  }

  /** Lower-cased, with spaces and hyphens folded to underscores, so all spellings converge. */
  private String normalize(String raw) {
    return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }

  /** The constant's own ID, falling back to the map key — which is the ID it was stored under. */
  private String idOf(Map.Entry<String, MatchTypeConstant> entry) {
    return entry.getValue().getId() != null ? entry.getValue().getId() : entry.getKey();
  }

  private Long parseId(String raw) {
    try {
      return Long.valueOf(raw);
    } catch (NumberFormatException e) {
      log.debug("Game mode constant has a non-numeric ID: {}", raw);
      return null;
    }
  }
}
