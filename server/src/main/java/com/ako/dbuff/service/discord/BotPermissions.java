package com.ako.dbuff.service.discord;

import java.util.EnumSet;
import java.util.Set;
import net.dv8tion.jda.api.Permission;

/**
 * What the bot needs in a channel to report matches. One definition, read by the readiness check
 * and the invite link, so the two cannot disagree about what "set up correctly" means.
 */
public final class BotPermissions {

  public static final Set<Permission> REQUIRED =
      EnumSet.of(
          Permission.VIEW_CHANNEL,
          Permission.MESSAGE_SEND,
          Permission.MESSAGE_SEND_IN_THREADS,
          Permission.CREATE_PUBLIC_THREADS,
          Permission.MESSAGE_EMBED_LINKS,
          Permission.MESSAGE_HISTORY);

  private BotPermissions() {}

  /** OAuth2 invite URL requesting the required permissions plus slash-command scope. */
  public static String inviteUrl(String clientId) {
    return "https://discord.com/oauth2/authorize?client_id="
        + clientId
        + "&scope=bot%20applications.commands&permissions="
        + Permission.getRaw(REQUIRED);
  }
}
