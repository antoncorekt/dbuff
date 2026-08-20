package com.ako.dbuff.service.discord.command;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Where a command writes its results after acknowledging.
 *
 * <p>Obtained from {@link CommandContext#acknowledge}. Backed by a freshly created thread when the
 * command was invoked in a text channel, or by the current thread when it was invoked inside one —
 * handlers do not need to know which.
 *
 * <p>Implementations are used from a virtual thread and may block.
 */
public interface AsyncReply {

  /** Posts a plain message, splitting it if it exceeds Discord's length limit. */
  void post(String message);

  /** Posts each message in order. Convenience for formatters that return pre-split chunks. */
  default void postLines(List<String> messages) {
    messages.forEach(this::post);
  }

  /** Posts an embed. */
  void postEmbed(MessageEmbed embed);

  /**
   * Reports that the command failed after acknowledging.
   *
   * <p>Distinct from {@link CommandContext#replyEphemeral} because by this point a thread exists
   * and the user is watching it — an ephemeral reply would be invisible there.
   */
  void fail(String message);
}
