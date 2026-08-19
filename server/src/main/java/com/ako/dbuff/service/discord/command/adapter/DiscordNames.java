package com.ako.dbuff.service.discord.command.adapter;

/** Shared naming rules for Discord objects created by the command layer. */
final class DiscordNames {

  /** Discord truncates thread names beyond this. */
  private static final int MAX_THREAD_NAME_LENGTH = 100;

  private DiscordNames() {}

  /**
   * Collapses whitespace and truncates to Discord's thread-name limit, falling back to a generic
   * name when nothing usable is left.
   *
   * @param rawName the desired thread name
   * @return a name Discord will accept
   */
  static String sanitizeThreadName(String rawName) {
    String name = rawName == null ? "DBuff" : rawName.replaceAll("\\s+", " ").trim();
    if (name.isEmpty()) {
      name = "DBuff";
    }
    return name.length() > MAX_THREAD_NAME_LENGTH
        ? name.substring(0, MAX_THREAD_NAME_LENGTH)
        : name;
  }
}
