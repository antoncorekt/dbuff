package com.ako.dbuff.service.replay.report.chart;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.icon.EmojiNames;
import com.ako.dbuff.service.discord.icon.HttpIconImageSource;
import com.ako.dbuff.service.discord.icon.IconCandidates;
import com.ako.dbuff.service.discord.icon.IconImageSource;
import com.ako.dbuff.service.discord.icon.IconImages;
import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Small icon PNGs of abilities and heroes for table pictures: the synced file first, the CDN
 * second. Works whether or not Discord emoji sync is enabled.
 */
@Slf4j
@Component
public class SkillIconImages {
  public static final int SIZE_PX = 32;
  static final int CACHE_ENTRIES = 600;
  private static final String AD_SUFFIX = "_ad";

  private final Path directory;
  private final String cdnUrl;
  private final IconImageSource source;
  private final HeroKits kits;
  private final Cache<String, Optional<byte[]>> cache =
      Caffeine.newBuilder().maximumSize(CACHE_ENTRIES).build();

  @Autowired
  public SkillIconImages(
      @Value("${discord.icons.directory:icons}") String directory,
      @Value("${discord.icons.cdn-url:https://cdn.cloudflare.steamstatic.com}") String cdnUrl,
      HeroKits kits) {
    this(Path.of(directory), cdnUrl, new HttpIconImageSource(), kits);
  }

  SkillIconImages(Path directory, String cdnUrl, IconImageSource source, HeroKits kits) {
    this.directory = directory;
    this.cdnUrl = cdnUrl;
    this.source = source;
    this.kits = kits;
  }

  public Optional<byte[]> ability(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return Optional.empty();
    }
    Optional<byte[]> icon = cached(IconKind.ABILITY, internalName);
    if (icon.isEmpty() && internalName.endsWith(AD_SUFFIX)) {
      return cached(
          IconKind.ABILITY, internalName.substring(0, internalName.length() - AD_SUFFIX.length()));
    }
    return icon;
  }

  public Optional<byte[]> hero(long heroId) {
    return cached(IconKind.HERO, String.valueOf(heroId));
  }

  private Optional<byte[]> cached(IconKind kind, String key) {
    return cache.get(kind + ":" + key, k -> load(kind, key));
  }

  private Optional<byte[]> load(IconKind kind, String key) {
    try {
      String iconKey = kind == IconKind.HERO ? heroKey(key) : key;
      if (iconKey != null) {
        Path file =
            directory
                .resolve(kind.name().toLowerCase(Locale.ROOT))
                .resolve(EmojiNames.of(kind, iconKey) + ".png");
        if (Files.isRegularFile(file)) {
          return Optional.of(IconImages.toSquarePng(Files.readAllBytes(file), SIZE_PX));
        }
      }
      String img = img(kind, key);
      if (img != null && !img.isBlank()) {
        return Optional.of(IconImages.toSquarePng(source.fetch(cdnUrl + img), SIZE_PX));
      }
    } catch (IOException | RuntimeException e) {
      log.debug("No {} icon for {}: {}", kind, key, e.toString());
      return Optional.empty();
    }
    log.debug("No {} icon for {}: unknown", kind, key);
    return Optional.empty();
  }

  private String heroKey(String heroId) {
    return kits.hero(Long.parseLong(heroId)).map(IconCandidates::heroKey).orElse(null);
  }

  private String img(IconKind kind, String key) {
    if (kind == IconKind.HERO) {
      return kits.hero(Long.parseLong(key)).map(HeroConstant::getImg).orElse(null);
    }
    return kits.ability(key).map(AbilityConstant::getImg).orElse(null);
  }
}
