package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.discord.command.adapter.GuildCommandRegistrar;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Handles the bot entering and leaving servers: a new server gets its slash commands and a short
 * getting-started message at once, and a server that removes the bot stops being polled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuildLifecycleListener extends ListenerAdapter {

  private static final int EMBED_COLOR = 0x00AE86;

  private static final String WELCOME_TEXT =
      "Match reports and Dota 2 stats for your group. To get started:\n"
          + "1. Go to the channel where reports should appear.\n"
          + "2. Run `/dbuff register player:` and pick a player from the list.\n"
          + "3. Add the rest of the group with `/dbuff add`, then try `/stats`.\n\n"
          + "Setup commands need the **Manage Channel** permission."
          + " `/dbuff help` lists everything.";

  private final GuildCommandRegistrar guildCommandRegistrar;
  private final DbufInstanceConfigService instanceConfigService;

  @Override
  public void onReady(ReadyEvent event) {
    JDA jda = event.getJDA();
    log.info("Invite URL: {}", BotPermissions.inviteUrl(jda.getSelfUser().getId()));
    reconcileInstances(jda);
  }

  /**
   * Catches up on what happened while the app was down: an instance whose guild the bot is no
   * longer in is deactivated (no leave event arrives for a removal made offline), and one
   * registered before guild IDs were stored gets its guild ID filled in from its channel so the
   * leave handler can find it later.
   *
   * <p>Guilds Discord reports as unavailable count as joined — an outage must not deactivate
   * instances, because re-registering replaces their players.
   */
  private void reconcileInstances(JDA jda) {
    Set<String> knownGuildIds = new HashSet<>(jda.getUnavailableGuilds());
    jda.getGuilds().forEach(guild -> knownGuildIds.add(guild.getId()));

    for (DbufInstanceConfigResponse instance : instanceConfigService.getAllActive()) {
      try {
        String guildId = instance.getDiscordGuildId();
        if (guildId == null) {
          backfillGuildId(jda, instance);
        } else if (!knownGuildIds.contains(guildId)) {
          instanceConfigService.deactivate(instance.getId());
          log.info(
              "Bot is no longer in guild {}, deactivated instance {}", guildId, instance.getId());
        }
      } catch (Exception e) {
        log.error("Could not reconcile instance {}: {}", instance.getId(), e.getMessage(), e);
      }
    }
  }

  private void backfillGuildId(JDA jda, DbufInstanceConfigResponse instance) {
    if (instance.getDiscordChannelId() == null) {
      return;
    }
    TextChannel channel = jda.getTextChannelById(instance.getDiscordChannelId());
    if (channel != null) {
      instanceConfigService.update(
          instance.getId(),
          UpdateInstanceRequest.builder().discordGuildId(channel.getGuild().getId()).build());
    }
  }

  @Override
  public void onGuildJoin(GuildJoinEvent event) {
    Guild guild = event.getGuild();
    log.info("Joined guild {} ({})", guild.getName(), guild.getId());
    guildCommandRegistrar.register(guild);
    sendWelcome(guild);
  }

  @Override
  public void onGuildLeave(GuildLeaveEvent event) {
    String guildId = event.getGuild().getId();
    int deactivated = instanceConfigService.deactivateAllForGuild(guildId);
    log.info("Left guild {}, deactivated {} instance(s)", guildId, deactivated);
  }

  private void sendWelcome(Guild guild) {
    TextChannel channel = guild.getSystemChannel();
    if (channel == null || !channel.canTalk()) {
      channel =
          guild.getTextChannels().stream().filter(TextChannel::canTalk).findFirst().orElse(null);
    }
    if (channel == null) {
      log.warn("No channel to welcome guild {} in", guild.getId());
      return;
    }
    if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
      channel.sendMessage(WELCOME_TEXT).queue();
      return;
    }
    channel
        .sendMessageEmbeds(
            new EmbedBuilder()
                .setTitle("👋 DBuff is here")
                .setDescription(WELCOME_TEXT)
                .setColor(EMBED_COLOR)
                .build())
        .queue();
  }
}
