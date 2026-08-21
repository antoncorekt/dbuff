package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Offers the players tracked by the channel's instance — the focus group.
 *
 * <p>Serves {@code /stats} and {@code /hero} across every subcommand. List-valued, because both fan
 * out over several players.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackedPlayerAutocomplete implements AutocompleteProvider {

  private final DbufInstanceConfigService instanceConfigService;

  @Override
  public String getOptionName() {
    return "player";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public Set<String> getCommandNames() {
    return Set.of("stats", "hero");
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, trackedPlayers(context));
    } catch (Exception e) {
      log.debug("Tracked player autocomplete failed for '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  /**
   * The focus group as display-name to submitted-value pairs.
   *
   * <p>Both are the player name: it is what the handler resolves against the instance, and it stays
   * legible if the user edits the option by hand.
   */
  Map<String, String> trackedPlayers(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config =
        instanceConfigService.getByDiscordChannelId(context.getParentChannelId());
    if (config.isEmpty() || config.get().getPlayers() == null) {
      return Map.of();
    }

    Map<String, String> candidates = new LinkedHashMap<>();
    for (PlayerInfo player : config.get().getPlayers()) {
      if (player.getName() != null && !player.getName().isBlank()) {
        candidates.put(player.getName(), player.getName());
      }
    }
    return candidates;
  }
}
