package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Compares the table with the emoji Discord really has, so one deleted by hand in the developer
 * portal is never posted as a broken tag: it is marked MISSING and the next sync creates it again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IconRegistryCheck {
  private final DiscordIconRepo repo;
  private final EmojiGateway gateway;
  private final IconService iconService;

  /** Returns how many icons were marked MISSING. */
  public int verify() {
    Set<Long> remote =
        gateway.list().stream().map(EmojiGateway.RemoteEmoji::id).collect(Collectors.toSet());
    int missing = 0;
    for (DiscordIconDomain row : repo.findByStatus(IconStatus.SYNCED)) {
      if (row.getEmojiId() == null || !remote.contains(row.getEmojiId())) {
        row.setStatus(IconStatus.MISSING);
        row.setError("Emoji not found on Discord");
        row.setUpdatedAt(LocalDateTime.now());
        repo.save(row);
        missing++;
      }
    }
    iconService.refresh();
    if (missing > 0) {
      log.warn("{} icon emoji are missing on Discord; sync icons to create them again", missing);
    }
    return missing;
  }
}
