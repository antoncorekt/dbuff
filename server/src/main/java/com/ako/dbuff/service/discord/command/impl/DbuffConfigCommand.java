package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code /dbuff} — instance configuration, replacing the {@code !dbuf} text commands.
 *
 * <p>All subcommands live in one class because {@link CommandRegistry} keys commands by root name
 * and Discord rejects two definitions sharing a name. Each subcommand delegates to a small private
 * method to keep this readable.
 */
@Slf4j
@Component
public class DbuffConfigCommand implements DbuffCommand {

  private static final int EMBED_COLOR = 0x00AE86;

  private final DbufInstanceConfigService instanceConfigService;
  private final PlayerReferenceResolver playerResolver;
  private final PlayerRepo playerRepo;
  private final CommandRegistry registry;

  public DbuffConfigCommand(
      DbufInstanceConfigService instanceConfigService,
      PlayerReferenceResolver playerResolver,
      PlayerRepo playerRepo,
      @Lazy CommandRegistry registry) {
    this.instanceConfigService = instanceConfigService;
    this.playerResolver = playerResolver;
    this.playerRepo = playerRepo;
    // Lazy because the registry collects every DbuffCommand, including this one.
    this.registry = registry;
  }

  @Override
  public String getName() {
    return "dbuff";
  }

  @Override
  public List<String> getTextAliases() {
    return List.of("dbuf");
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash("dbuff", "Configure match tracking for this channel")
        .addSubcommands(
            new SubcommandData("register", "Register this channel for match tracking")
                .addOptions(
                    new OptionData(OptionType.STRING, "player", "Player to track", true, true),
                    new OptionData(OptionType.STRING, "name", "Name for this instance", false),
                    new OptionData(OptionType.STRING, "mode", "Game mode to track", false, true)),
            new SubcommandData("status", "Show this channel's configuration"),
            new SubcommandData("add", "Add a player to tracking")
                .addOptions(
                    new OptionData(OptionType.STRING, "player", "Player to add", true, true),
                    new OptionData(
                        OptionType.USER,
                        "user",
                        "Discord account to link at the same time",
                        false)),
            new SubcommandData("remove", "Remove a player from tracking")
                .addOptions(
                    new OptionData(OptionType.STRING, "player", "Player to remove", true, true)),
            new SubcommandData("link", "Link a Discord account to a tracked player")
                .addOptions(
                    new OptionData(OptionType.STRING, "player", "Tracked player", true, true),
                    new OptionData(OptionType.USER, "user", "Discord account", true)),
            new SubcommandData("modes", "Add or remove a tracked game mode")
                .addOptions(
                    new OptionData(OptionType.STRING, "mode", "Game mode", true, true),
                    new OptionData(OptionType.BOOLEAN, "remove", "Remove instead of add", false)),
            new SubcommandData("deactivate", "Stop tracking in this channel"),
            new SubcommandData("help", "List every command"));
  }

  @Override
  public Map<String, String> parseTextArguments(String alias, String subcommand, String arguments) {
    Map<String, String> options = new HashMap<>();
    if (arguments == null || arguments.isBlank()) {
      return options;
    }

    // Legacy forms put the player list first and flags after: `111 222 --modes 22 --name Squad`.
    String[] tokens = arguments.trim().split("\\s+");
    StringBuilder players = new StringBuilder();
    String flag = null;
    StringBuilder flagValue = new StringBuilder();

    for (String token : tokens) {
      if (token.startsWith("--")) {
        storeFlag(options, flag, flagValue);
        flag = token.substring(2).toLowerCase();
        flagValue.setLength(0);
      } else if (flag != null) {
        if (flagValue.length() > 0) {
          flagValue.append(' ');
        }
        flagValue.append(token);
      } else {
        if (players.length() > 0) {
          players.append(',');
        }
        players.append(token);
      }
    }
    storeFlag(options, flag, flagValue);

    if (players.length() > 0) {
      options.put("player", players.toString());
    }
    return options;
  }

  private void storeFlag(Map<String, String> options, String flag, StringBuilder value) {
    if (flag == null || value.length() == 0) {
      return;
    }
    // `--modes` was plural in the legacy syntax; the option is `mode`.
    options.put(flag.equals("modes") ? "mode" : flag, value.toString());
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    switch (subcommand == null ? "help" : subcommand) {
      case "register" -> register(context);
      case "status" -> status(context);
      case "add" -> addPlayer(context);
      case "remove" -> removePlayer(context);
      case "link" -> link(context);
      case "modes" -> modes(context);
      case "deactivate" -> deactivate(context);
      default -> help(context);
    }
  }

  private void register(CommandContext context) {
    List<String> playerRefs = context.getOptionAsList("player");
    if (playerRefs.isEmpty()) {
      context.replyEphemeral("❌ Provide at least one player to track.");
      return;
    }

    Set<Long> playerIds = numericIdsOrEmpty(playerRefs);
    if (playerIds.isEmpty()) {
      context.replyEphemeral(
          "❌ Could not read a player from `"
              + String.join(", ", playerRefs)
              + "`. Pick one from the autocomplete list, which submits the account ID.");
      return;
    }

    String mode = context.getOption("mode");
    RegisterInstanceRequest request =
        RegisterInstanceRequest.builder()
            .playerIds(playerIds)
            .gameModes(mode == null || mode.isBlank() ? null : Set.of(mode))
            .discordChannelId(context.getParentChannelId())
            .discordGuildId(context.getGuildId().orElse(null))
            .name(context.getOption("name"))
            .build();

    try {
      DbufInstanceConfigResponse response = instanceConfigService.register(request);
      AsyncReply reply = context.acknowledge("✅ Registered this channel.", "dbuff: register");
      reply.postEmbed(configEmbed("✅ Registration Successful", response));
    } catch (IllegalStateException e) {
      context.replyEphemeral(
          "❌ This channel already has a registered instance. Use `/dbuff status` to view it.");
    } catch (IllegalArgumentException e) {
      context.replyEphemeral("❌ " + e.getMessage());
    }
  }

  private void status(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config = currentInstance(context);
    if (config.isEmpty()) {
      context.replyEphemeral(
          "ℹ️ No instance registered for this channel. Use `/dbuff register` to start.");
      return;
    }
    AsyncReply reply = context.acknowledge("📊 Fetching configuration…", "dbuff: status");
    reply.postEmbed(configEmbed("📊 Instance Configuration", config.get()));
  }

  private void addPlayer(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config = currentInstance(context);
    if (config.isEmpty()) {
      context.replyEphemeral("❌ No instance registered. Use `/dbuff register` first.");
      return;
    }

    List<String> playerRefs = context.getOptionAsList("player");
    Set<Long> playerIds = numericIdsOrEmpty(playerRefs);
    if (playerIds.isEmpty()) {
      context.replyEphemeral(
          "❌ Could not read a player from `"
              + String.join(", ", playerRefs)
              + "`. Pick one from the autocomplete list, which submits the account ID.");
      return;
    }

    DbufInstanceConfigResponse updated =
        instanceConfigService.update(
            config.get().getId(), UpdateInstanceRequest.builder().addPlayerIds(playerIds).build());

    AsyncReply reply = context.acknowledge("✅ Adding player…", "dbuff: add player");

    context
        .getOptionAsUserId("user")
        .ifPresent(
            discordUserId ->
                playerIds.forEach(playerId -> reply.post(linkAccount(playerId, discordUserId))));

    reply.postEmbed(configEmbed("✅ Players Added", updated));
  }

  private void removePlayer(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config = currentInstance(context);
    if (config.isEmpty()) {
      context.replyEphemeral("❌ No instance registered. Use `/dbuff register` first.");
      return;
    }

    PlayerReferenceResolver.Resolution resolution =
        playerResolver.resolve(context.getParentChannelId(), context.getOptionAsList("player"));
    if (resolution.hasUnresolved()) {
      context.replyEphemeral(unresolvedMessage(context, resolution));
      return;
    }
    if (resolution.isEmpty()) {
      context.replyEphemeral("❌ Provide a player to remove.");
      return;
    }

    Set<Long> playerIds =
        resolution.players().stream()
            .map(PlayerReferenceResolver.ResolvedPlayer::accountId)
            .collect(Collectors.toSet());

    DbufInstanceConfigResponse updated =
        instanceConfigService.update(
            config.get().getId(),
            UpdateInstanceRequest.builder().removePlayerIds(playerIds).build());

    AsyncReply reply = context.acknowledge("✅ Removing player…", "dbuff: remove player");
    reply.postEmbed(configEmbed("✅ Players Removed", updated));
  }

  private void link(CommandContext context) {
    Optional<String> discordUserId = context.getOptionAsUserId("user");
    if (discordUserId.isEmpty()) {
      context.replyEphemeral("❌ Mention the Discord account to link.");
      return;
    }

    PlayerReferenceResolver.Resolution resolution =
        playerResolver.resolve(context.getParentChannelId(), context.getOptionAsList("player"));
    if (resolution.hasUnresolved()) {
      context.replyEphemeral(unresolvedMessage(context, resolution));
      return;
    }
    if (resolution.isEmpty()) {
      context.replyEphemeral("❌ Provide the player to link.");
      return;
    }

    AsyncReply reply = context.acknowledge("🔗 Linking…", "dbuff: link");
    resolution
        .players()
        .forEach(player -> reply.post(linkAccount(player.accountId(), discordUserId.get())));
  }

  private void modes(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config = currentInstance(context);
    if (config.isEmpty()) {
      context.replyEphemeral("❌ No instance registered. Use `/dbuff register` first.");
      return;
    }

    String mode = context.getOption("mode");
    if (mode == null || mode.isBlank()) {
      context.replyEphemeral("❌ Provide a game mode.");
      return;
    }

    boolean remove = "true".equalsIgnoreCase(context.getOption("remove"));
    UpdateInstanceRequest request =
        remove
            ? UpdateInstanceRequest.builder().removeGameModes(Set.of(mode)).build()
            : UpdateInstanceRequest.builder().addGameModes(Set.of(mode)).build();

    try {
      DbufInstanceConfigResponse updated =
          instanceConfigService.update(config.get().getId(), request);
      AsyncReply reply = context.acknowledge("✅ Updating game modes…", "dbuff: modes");
      reply.postEmbed(configEmbed(remove ? "✅ Game Mode Removed" : "✅ Game Mode Added", updated));
    } catch (IllegalArgumentException e) {
      context.replyEphemeral("❌ " + e.getMessage());
    }
  }

  private void deactivate(CommandContext context) {
    Optional<DbufInstanceConfigResponse> config = currentInstance(context);
    if (config.isEmpty()) {
      context.replyEphemeral("❌ No instance registered for this channel.");
      return;
    }

    instanceConfigService.deactivate(config.get().getId());
    AsyncReply reply = context.acknowledge("⏹️ Deactivating…", "dbuff: deactivate");
    reply.post("✅ Instance deactivated. Use `/dbuff register` to start tracking again.");
  }

  private void help(CommandContext context) {
    AsyncReply reply = context.acknowledge("📖 Commands:", "dbuff: help");
    reply.postEmbed(registry.buildHelpEmbed());
  }

  /**
   * Writes the Discord link onto the player row.
   *
   * @return a human-readable outcome for the thread
   */
  private String linkAccount(Long accountId, String discordUserId) {
    List<PlayerDomain> players = playerRepo.findByAccountIds(List.of(accountId));
    if (players.isEmpty()) {
      return "⚠️ Player `"
          + accountId
          + "` is not in the database yet — link again after their first tracked match.";
    }
    PlayerDomain player = players.get(0);
    player.setDiscordUserId(discordUserId);
    playerRepo.save(player);
    log.info("Linked player {} to Discord user {}", accountId, discordUserId);
    return "🔗 Linked **" + player.getName() + "** to <@" + discordUserId + ">.";
  }

  private Optional<DbufInstanceConfigResponse> currentInstance(CommandContext context) {
    return instanceConfigService.getByDiscordChannelId(context.getParentChannelId());
  }

  /**
   * Account IDs for entries that are already numeric.
   *
   * <p>Used by {@code register} and {@code add}, where the player may not be in the focus group
   * yet, so name resolution is not possible — the autocomplete submits the numeric ID for exactly
   * this reason.
   */
  private Set<Long> numericIdsOrEmpty(List<String> references) {
    return references.stream()
        .map(String::trim)
        .filter(reference -> !reference.isEmpty())
        .filter(reference -> reference.chars().allMatch(Character::isDigit))
        .map(Long::parseLong)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private String unresolvedMessage(
      CommandContext context, PlayerReferenceResolver.Resolution resolution) {
    StringBuilder message = new StringBuilder("❌ Could not find: ");
    List<String> parts =
        resolution.unresolved().stream()
            .map(
                unknown ->
                    "`"
                        + unknown
                        + "`"
                        + playerResolver
                            .suggest(context.getParentChannelId(), unknown)
                            .map(suggestion -> " (did you mean `" + suggestion + "`?)")
                            .orElse(""))
            .toList();
    return message.append(String.join(", ", parts)).toString();
  }

  private MessageEmbed configEmbed(String title, DbufInstanceConfigResponse config) {
    return new EmbedBuilder()
        .setTitle(title)
        .addField("Instance ID", config.getId(), false)
        .addField("Name", config.getName() == null ? "Not set" : config.getName(), true)
        .addField(
            "Status", Boolean.TRUE.equals(config.getActive()) ? "✅ Active" : "❌ Inactive", true)
        .addField("Players", formatPlayers(config.getPlayers()), false)
        .addField(
            "Game Modes",
            config.getGameModes() == null || config.getGameModes().isEmpty()
                ? "All modes"
                : config.getGameModes().toString(),
            false)
        .setColor(EMBED_COLOR)
        .build();
  }

  private String formatPlayers(Set<PlayerInfo> players) {
    if (players == null || players.isEmpty()) {
      return "None";
    }
    return players.stream().map(player -> formatPlayer(player)).collect(Collectors.joining(", "));
  }

  /** Renders a player, appending the Discord mention when one is linked. */
  private String formatPlayer(PlayerInfo player) {
    String base = player.getName() + " (" + player.getId() + ")";
    if (player.getId() == null) {
      return base;
    }
    return playerRepo.findByAccountIds(List.of(player.getId())).stream()
        .map(PlayerDomain::getDiscordUserId)
        .filter(id -> id != null && !id.isBlank())
        .findFirst()
        .map(id -> base + " — <@" + id + ">")
        .orElse(base);
  }
}
