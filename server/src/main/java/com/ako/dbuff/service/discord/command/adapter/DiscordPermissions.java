package com.ako.dbuff.service.discord.command.adapter;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * Permission checks shared by every {@link com.ako.dbuff.service.discord.command.CommandContext}.
 */
final class DiscordPermissions {

  private DiscordPermissions() {}

  /**
   * Whether {@code member} may change configuration for {@code channel}: Manage Server, or Manage
   * Channel on the channel. A thread is judged by its parent, since that is the channel an instance
   * is registered against.
   *
   * @param member the invoker; null in a direct message
   * @param channel the channel the command ran in
   */
  static boolean canManageChannel(Member member, Channel channel) {
    if (member == null) {
      return false;
    }
    if (member.hasPermission(Permission.MANAGE_SERVER)) {
      return true;
    }
    GuildChannel target =
        channel instanceof ThreadChannel thread
            ? thread.getParentChannel()
            : channel instanceof GuildChannel guildChannel ? guildChannel : null;
    return target != null && member.hasPermission(target, Permission.MANAGE_CHANNEL);
  }
}
