package com.ako.dbuff.service.ask.discord;

import com.ako.dbuff.service.discord.command.adapter.TextCommandParser;

/** Decides, without JDA, whether a Discord message starts or continues an ask conversation. */
public final class AskTrigger {

  public enum Decision {
    NEW_QUESTION,
    FOLLOW_UP,
    IGNORE
  }

  /**
   * The facts of one message that matter to the decision.
   *
   * @param botAuthor the author is a bot
   * @param inThread the message was sent in a thread
   * @param askThread the thread is one the ask feature holds a conversation in
   * @param content the raw message text
   * @param hasImage the message carries an image attachment
   * @param textPrefix the prefix of text commands
   */
  public record MessageFacts(
      boolean botAuthor,
      boolean inThread,
      boolean askThread,
      String content,
      boolean hasImage,
      String textPrefix) {}

  private AskTrigger() {}

  public static Decision decide(MessageFacts facts) {
    if (facts.botAuthor() || facts.hasImage()) {
      return Decision.IGNORE;
    }
    String content = facts.content();
    if (content == null || content.isBlank() || content.stripLeading().startsWith("/")) {
      return Decision.IGNORE;
    }
    if (TextCommandParser.parse(facts.textPrefix(), content).isPresent()) {
      return Decision.IGNORE;
    }
    if (facts.inThread()) {
      return facts.askThread() ? Decision.FOLLOW_UP : Decision.IGNORE;
    }
    return Decision.NEW_QUESTION;
  }
}
