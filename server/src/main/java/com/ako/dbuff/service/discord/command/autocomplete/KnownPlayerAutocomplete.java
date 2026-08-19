package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Offers players already seen in processed matches, for {@code /scout player}.
 *
 * <p>Single-valued, and free text still works: {@code /scout} keeps the regex behaviour of the old
 * {@code !vs}, so a name the bot has never seen is a legitimate input rather than an error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnownPlayerAutocomplete implements AutocompleteProvider {

  /**
   * The player table can hold every account seen in every processed match, so it is paged rather
   * than loaded whole. Larger than Discord's 25-choice cap because the cap applies after filtering.
   */
  private static final int MAX_PLAYERS_SCANNED = 500;

  private final PlayerRepo playerRepo;

  @Override
  public String getOptionName() {
    return "name";
  }

  @Override
  public String getCommandName() {
    return "scout";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, knownPlayers());
    } catch (Exception e) {
      log.debug("Known player autocomplete failed for '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> knownPlayers() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (PlayerDomain player :
        playerRepo.findAll(PageRequest.of(0, MAX_PLAYERS_SCANNED)).getContent()) {
      if (player.getName() != null && !player.getName().isBlank()) {
        candidates.put(player.getName(), player.getName());
      }
    }
    return candidates;
  }
}
