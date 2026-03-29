package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Discord listener for handling Dbuf instance registration and management commands.
 *
 * <p>Commands: - !dbuf register <player_ids> [--modes <game_modes>] [--name <name>] - !dbuf status
 * - !dbuf add-players <player_ids> - !dbuf remove-players <player_ids> - !dbuf add-modes
 * <game_modes> - !dbuf remove-modes <game_modes> - !dbuf deactivate - !dbuf help
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationDiscordListener extends ListenerAdapter {

  private static final String COMMAND_PREFIX = "!dbuf";

  private final DbufInstanceConfigService instanceConfigService;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) {
      return;
    }

    String content = event.getMessage().getContentRaw().trim();
    if (!content.startsWith(COMMAND_PREFIX)) {
      return;
    }

    String[] parts = content.substring(COMMAND_PREFIX.length()).trim().split("\\s+", 2);
    if (parts.length == 0 || parts[0].isEmpty()) {
      sendHelp(event.getChannel());
      return;
    }

    String command = parts[0].toLowerCase();
    String args = parts.length > 1 ? parts[1] : "";

    String channelId = event.getChannel().getId();
    String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

    try {
      switch (command) {
        case "register" -> handleRegister(event.getChannel(), channelId, guildId, args);
        case "status" -> handleStatus(event.getChannel(), channelId);
        case "add-players" -> handleAddPlayers(event.getChannel(), channelId, args);
        case "remove-players" -> handleRemovePlayers(event.getChannel(), channelId, args);
        case "add-modes" -> handleAddModes(event.getChannel(), channelId, args);
        case "remove-modes" -> handleRemoveModes(event.getChannel(), channelId, args);
        case "deactivate" -> handleDeactivate(event.getChannel(), channelId);
        case "help" -> sendHelp(event.getChannel());
        default -> sendHelp(event.getChannel());
      }
    } catch (Exception e) {
      log.error("Error handling Discord command: {}", e.getMessage(), e);
      event.getChannel().sendMessage("❌ Error: " + e.getMessage()).queue();
    }
  }

  private void handleRegister(
      MessageChannel channel, String channelId, String guildId, String args) {
    // Parse arguments: <player_ids> [--modes <game_modes>] [--name <name>]
    Set<Long> playerIds = new HashSet<>();
    Set<String> gameModes = new HashSet<>();
    String name = null;

    String[] tokens = args.split("\\s+");
    int i = 0;

    // Parse player IDs (before any flags)
    while (i < tokens.length && !tokens[i].startsWith("--")) {
      try {
        playerIds.add(Long.parseLong(tokens[i]));
      } catch (NumberFormatException e) {
        channel.sendMessage("❌ Invalid player ID: " + tokens[i]).queue();
        return;
      }
      i++;
    }

    // Parse flags
    while (i < tokens.length) {
      if ("--modes".equals(tokens[i]) && i + 1 < tokens.length) {
        i++;
        // Collect modes until next flag or end
        while (i < tokens.length && !tokens[i].startsWith("--")) {
          gameModes.add(tokens[i]);
          i++;
        }
      } else if ("--name".equals(tokens[i]) && i + 1 < tokens.length) {
        i++;
        StringBuilder nameBuilder = new StringBuilder();
        while (i < tokens.length && !tokens[i].startsWith("--")) {
          if (nameBuilder.length() > 0) nameBuilder.append(" ");
          nameBuilder.append(tokens[i]);
          i++;
        }
        name = nameBuilder.toString();
      } else {
        i++;
      }
    }

    if (playerIds.isEmpty()) {
      channel
          .sendMessage(
              "❌ Please provide at least one player ID. Usage: `!dbuf register <player_ids>`")
          .queue();
      return;
    }

    RegisterInstanceRequest request =
        RegisterInstanceRequest.builder()
            .playerIds(playerIds)
            .gameModes(gameModes.isEmpty() ? null : gameModes)
            .discordChannelId(channelId)
            .discordGuildId(guildId)
            .name(name)
            .build();

    try {
      DbufInstanceConfigResponse response = instanceConfigService.register(request);
      channel.sendMessageEmbeds(createSuccessEmbed("✅ Registration Successful", response)).queue();
      log.info(
          "Discord registration successful: channelId={}, instanceId={}",
          channelId,
          response.getId());
    } catch (IllegalStateException e) {
      channel
          .sendMessage(
              "❌ This channel already has a registered instance. Use `!dbuf status` to view it.")
          .queue();
    } catch (IllegalArgumentException e) {
      channel.sendMessage("❌ " + e.getMessage()).queue();
    }
  }

  private void handleStatus(MessageChannel channel, String channelId) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);

    if (configOpt.isEmpty()) {
      channel
          .sendMessage(
              "ℹ️ No instance registered for this channel. Use `!dbuf register <player_ids>` to register.")
          .queue();
      return;
    }

    channel.sendMessageEmbeds(createStatusEmbed(configOpt.get())).queue();
  }

  private void handleAddPlayers(MessageChannel channel, String channelId, String args) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (configOpt.isEmpty()) {
      channel
          .sendMessage("❌ No instance registered for this channel. Use `!dbuf register` first.")
          .queue();
      return;
    }

    Set<Long> playerIds = parsePlayerIds(args);
    if (playerIds.isEmpty()) {
      channel.sendMessage("❌ Please provide player IDs to add.").queue();
      return;
    }

    UpdateInstanceRequest request = UpdateInstanceRequest.builder().addPlayerIds(playerIds).build();

    DbufInstanceConfigResponse response =
        instanceConfigService.update(configOpt.get().getId(), request);
    channel.sendMessageEmbeds(createSuccessEmbed("✅ Players Added", response)).queue();
  }

  private void handleRemovePlayers(MessageChannel channel, String channelId, String args) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (configOpt.isEmpty()) {
      channel
          .sendMessage("❌ No instance registered for this channel. Use `!dbuf register` first.")
          .queue();
      return;
    }

    Set<Long> playerIds = parsePlayerIds(args);
    if (playerIds.isEmpty()) {
      channel.sendMessage("❌ Please provide player IDs to remove.").queue();
      return;
    }

    UpdateInstanceRequest request =
        UpdateInstanceRequest.builder().removePlayerIds(playerIds).build();

    DbufInstanceConfigResponse response =
        instanceConfigService.update(configOpt.get().getId(), request);
    channel.sendMessageEmbeds(createSuccessEmbed("✅ Players Removed", response)).queue();
  }

  private void handleAddModes(MessageChannel channel, String channelId, String args) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (configOpt.isEmpty()) {
      channel
          .sendMessage("❌ No instance registered for this channel. Use `!dbuf register` first.")
          .queue();
      return;
    }

    Set<String> modes = parseGameModes(args);
    if (modes.isEmpty()) {
      channel.sendMessage("❌ Please provide game modes to add.").queue();
      return;
    }

    UpdateInstanceRequest request = UpdateInstanceRequest.builder().addGameModes(modes).build();

    DbufInstanceConfigResponse response =
        instanceConfigService.update(configOpt.get().getId(), request);
    channel.sendMessageEmbeds(createSuccessEmbed("✅ Game Modes Added", response)).queue();
  }

  private void handleRemoveModes(MessageChannel channel, String channelId, String args) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (configOpt.isEmpty()) {
      channel
          .sendMessage("❌ No instance registered for this channel. Use `!dbuf register` first.")
          .queue();
      return;
    }

    Set<String> modes = parseGameModes(args);
    if (modes.isEmpty()) {
      channel.sendMessage("❌ Please provide game modes to remove.").queue();
      return;
    }

    UpdateInstanceRequest request = UpdateInstanceRequest.builder().removeGameModes(modes).build();

    DbufInstanceConfigResponse response =
        instanceConfigService.update(configOpt.get().getId(), request);
    channel.sendMessageEmbeds(createSuccessEmbed("✅ Game Modes Removed", response)).queue();
  }

  private void handleDeactivate(MessageChannel channel, String channelId) {
    Optional<DbufInstanceConfigResponse> configOpt =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (configOpt.isEmpty()) {
      channel.sendMessage("❌ No instance registered for this channel.").queue();
      return;
    }

    instanceConfigService.deactivate(configOpt.get().getId());
    channel.sendMessage("✅ Instance deactivated successfully.").queue();
  }

  private void sendHelp(MessageChannel channel) {
    EmbedBuilder embed =
        new EmbedBuilder()
            .setTitle("📖 Dbuf Bot Commands")
            .setDescription("Manage your Dota 2 match tracking configuration")
            .addField(
                "Register",
                "`!dbuf register <player_ids> [--modes <modes>] [--name <name>]`\nRegister this channel for match tracking",
                false)
            .addField("Status", "`!dbuf status`\nView current configuration", false)
            .addField(
                "Add Players", "`!dbuf add-players <player_ids>`\nAdd players to track", false)
            .addField(
                "Remove Players",
                "`!dbuf remove-players <player_ids>`\nRemove players from tracking",
                false)
            .addField("Add Modes", "`!dbuf add-modes <game_modes>`\nAdd game modes to track", false)
            .addField(
                "Remove Modes",
                "`!dbuf remove-modes <game_modes>`\nRemove game modes from tracking",
                false)
            .addField("Deactivate", "`!dbuf deactivate`\nDeactivate this instance", false)
            .setColor(0x00AE86);

    channel.sendMessageEmbeds(embed.build()).queue();
  }

  private Set<Long> parsePlayerIds(String args) {
    if (args == null || args.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(args.split("\\s+"))
        .filter(s -> !s.isBlank())
        .map(
            s -> {
              try {
                return Long.parseLong(s);
              } catch (NumberFormatException e) {
                return null;
              }
            })
        .filter(id -> id != null)
        .collect(Collectors.toSet());
  }

  private Set<String> parseGameModes(String args) {
    if (args == null || args.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(args.split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
  }

  private MessageEmbed createSuccessEmbed(String title, DbufInstanceConfigResponse config) {
    return new EmbedBuilder()
        .setTitle(title)
        .addField("Instance ID", config.getId(), false)
        .addField("Players", formatPlayers(config.getPlayers()), false)
        .addField(
            "Game Modes",
            config.getGameModes().isEmpty() ? "All" : config.getGameModes().toString(),
            false)
        .addField("Status", config.getActive() ? "Active" : "Inactive", true)
        .setColor(0x00AE86)
        .build();
  }

  private MessageEmbed createStatusEmbed(DbufInstanceConfigResponse config) {
    EmbedBuilder embed =
        new EmbedBuilder()
            .setTitle("📊 Instance Configuration")
            .addField("Instance ID", config.getId(), false)
            .addField("Name", config.getName() != null ? config.getName() : "Not set", true)
            .addField("Status", config.getActive() ? "✅ Active" : "❌ Inactive", true)
            .addField("Players", formatPlayers(config.getPlayers()), false)
            .addField(
                "Game Modes",
                config.getGameModes().isEmpty() ? "All modes" : config.getGameModes().toString(),
                false)
            .addField(
                "Created",
                config.getCreatedAt() != null ? config.getCreatedAt().toString() : "Unknown",
                true)
            .addField(
                "Updated",
                config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : "Unknown",
                true)
            .setColor(0x00AE86);

    return embed.build();
  }

  /**
   * Formats player info for display in Discord embeds. Shows player name with ID in parentheses.
   */
  private String formatPlayers(Set<PlayerInfo> players) {
    if (players == null || players.isEmpty()) {
      return "None";
    }
    return players.stream()
        .map(p -> String.format("%s (%d)", p.getName(), p.getId()))
        .collect(Collectors.joining(", "));
  }
}
