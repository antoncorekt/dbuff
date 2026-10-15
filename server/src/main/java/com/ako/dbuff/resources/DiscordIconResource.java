package com.ako.dbuff.resources;

import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.discord.icon.IconProperties;
import com.ako.dbuff.service.discord.icon.IconSyncService;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner operation: create the bot's application emoji for heroes, abilities and items. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/discord/icons")
public class DiscordIconResource {
  private static final String ALL = "ALL";

  private final IconSyncService sync;
  private final IconProperties properties;

  @PostMapping("/sync")
  public ResponseEntity<Map<String, String>> sync(
      @RequestParam(defaultValue = ALL) String kind,
      @RequestParam(defaultValue = "false") boolean all) {
    Set<IconKind> kinds = parse(kind);
    if (kinds == null) {
      return error(HttpStatus.BAD_REQUEST, "kind must be HERO, ITEM, ABILITY or ALL");
    }
    if (all && !kinds.equals(Set.of(IconKind.ABILITY))) {
      return error(HttpStatus.BAD_REQUEST, "all applies to kind ABILITY only");
    }
    if (!properties.isEnabled()) {
      return error(HttpStatus.CONFLICT, "Icons are switched off (discord.icons.enabled)");
    }
    if (!sync.start(kinds, all)) {
      return error(HttpStatus.CONFLICT, "An icon sync is already running");
    }
    return ResponseEntity.accepted().body(Map.of("status", "started"));
  }

  private static Set<IconKind> parse(String kind) {
    String upper = kind.toUpperCase(Locale.ROOT);
    if (ALL.equals(upper)) {
      return EnumSet.allOf(IconKind.class);
    }
    return Arrays.stream(IconKind.values())
        .filter(k -> k.name().equals(upper))
        .findFirst()
        .map(EnumSet::of)
        .orElse(null);
  }

  private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("error", message));
  }
}
