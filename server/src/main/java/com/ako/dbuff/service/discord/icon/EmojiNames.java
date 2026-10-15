package com.ako.dbuff.service.discord.icon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The one place that maps an icon to its emoji name: kind prefix plus key, letters, digits and
 * underscores only, at most 32 characters (Discord's limit). A key that had to be changed (unsafe
 * characters, too long) gets a short hash of the original key as suffix, so two keys that reduce to
 * the same text still differ; {@link #withHash} is the fallback when a name is taken anyway.
 */
public final class EmojiNames {
  public static final int MAX_LENGTH = 32;
  private static final int HASH_LENGTH = 6;

  private EmojiNames() {}

  public static String of(IconKind kind, String key) {
    String safe = safe(key);
    String plain = kind.getNamePrefix() + safe;
    if (safe.equals(key) && plain.length() <= MAX_LENGTH) {
      return plain;
    }
    return withHash(kind, key);
  }

  public static String withHash(IconKind kind, String key) {
    String suffix = "_" + hash(kind, key);
    String base = kind.getNamePrefix() + safe(key);
    int room = MAX_LENGTH - suffix.length();
    return (base.length() > room ? base.substring(0, room) : base) + suffix;
  }

  private static String safe(String key) {
    return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
  }

  private static String hash(IconKind kind, String key) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((kind.name() + ":" + key).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
