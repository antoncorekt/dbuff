package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.dotapi.api.SearchApi;
import com.ako.dbuff.dotapi.model.SearchResponse;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Searches all of OpenDota for a player to start tracking, for {@code /dbuff register} and {@code
 * /dbuff add}.
 *
 * <p>Registered for {@code /dbuff} generally rather than for one subcommand, because both of those
 * name players the channel does not track yet. {@code remove} and {@code link} have their own
 * providers and still win, since the adapter prefers a subcommand-specific provider over a general
 * one — searching all of OpenDota for a player to remove would offer millions of accounts that
 * cannot be removed.
 *
 * <p>The only autocomplete provider that makes a network call, which drives two decisions:
 *
 * <ul>
 *   <li>Its own Caffeine cache keyed on the query prefix, because autocomplete fires on every
 *       keystroke and a user typing eight characters would otherwise be eight API calls.
 *   <li>Its own {@link RateLimiter}, deliberately <em>not</em> the shared {@code
 *       dotaApiRateLimiter}. Sharing it would let someone typing in a search box exhaust the 60/min
 *       budget that match ingestion depends on.
 * </ul>
 *
 * <p>Never blocks waiting for a permit: if the budget is spent it returns nothing rather than
 * stalling the picker past Discord's three-second window.
 */
@Slf4j
@Component
public class OpenDotaPlayerAutocomplete implements AutocompleteProvider {

  /** Below this many characters a search matches far too much to be useful. */
  private static final int MIN_QUERY_LENGTH = 3;

  private static final int MAX_CACHE_ENTRIES = 500;
  private static final Duration CACHE_TTL = Duration.ofMinutes(10);

  private final SearchApi searchApi;
  private final RateLimiter rateLimiter;
  private final Cache<String, Map<String, String>> cache =
      Caffeine.newBuilder().maximumSize(MAX_CACHE_ENTRIES).expireAfterWrite(CACHE_TTL).build();

  public OpenDotaPlayerAutocomplete(
      SearchApi searchApi,
      @Qualifier("autocompleteSearchRateLimiter") RateLimiter autocompleteSearchRateLimiter) {
    this.searchApi = searchApi;
    this.rateLimiter = autocompleteSearchRateLimiter;
  }

  @Override
  public String getOptionName() {
    return "player";
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    String query = currentInput == null ? "" : currentInput.trim();
    if (query.length() < MIN_QUERY_LENGTH) {
      return List.of();
    }

    try {
      Map<String, String> cached = cache.getIfPresent(query.toLowerCase());
      if (cached != null) {
        return ChoiceAccumulator.single(query, cached);
      }

      if (!rateLimiter.tryAcquire()) {
        // Better an empty picker than one that times out and looks broken.
        log.debug("Skipping OpenDota search for '{}': autocomplete rate limit spent", query);
        return List.of();
      }

      Map<String, String> results = search(query);
      cache.put(query.toLowerCase(), results);
      return ChoiceAccumulator.single(query, results);
    } catch (Exception e) {
      log.debug("OpenDota player autocomplete failed for '{}': {}", query, e.getMessage());
      return List.of();
    }
  }

  /** Display {@code Name (12345678)}, submit the numeric account ID. */
  private Map<String, String> search(String query) throws Exception {
    List<SearchResponse> results = searchApi.getSearch(query);

    Map<String, String> candidates = new LinkedHashMap<>();
    if (results == null) {
      return candidates;
    }
    for (SearchResponse result : results) {
      Long accountId = result.getAccountId();
      if (accountId == null) {
        continue;
      }
      String name =
          result.getPersonaname() == null || result.getPersonaname().isBlank()
              ? "anonymous"
              : result.getPersonaname();
      candidates.put(name + " (" + accountId + ")", String.valueOf(accountId));
    }
    return candidates;
  }
}
