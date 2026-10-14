package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Creates the application emoji for the icons the constants describe. Only missing or changed icons
 * are touched, so a repeat run costs no uploads; a failure of one icon is recorded on its row and
 * the run goes on. Started by the owner, never at start-up.
 */
@Slf4j
@Service
public class IconSyncService {
  private static final int ERROR_LENGTH = 500;

  private final IconProperties properties;
  private final DiscordIconRepo repo;
  private final IconCandidates candidates;
  private final IconImageSource images;
  private final EmojiGateway gateway;
  private final IconService iconService;

  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "icon-sync");
            t.setDaemon(true);
            return t;
          });

  public IconSyncService(
      IconProperties properties,
      DiscordIconRepo repo,
      IconCandidates candidates,
      IconImageSource images,
      EmojiGateway gateway,
      IconService iconService) {
    this.properties = properties;
    this.repo = repo;
    this.candidates = candidates;
    this.images = images;
    this.gateway = gateway;
    this.iconService = iconService;
  }

  public record Report(int created, int replaced, int unchanged, int failed, int overLimit) {}

  /** Starts a run in the background; false when one is already running. */
  public boolean start(Set<IconKind> kinds, boolean allAbilities) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    executor.execute(
        () -> {
          try {
            Report report = run(kinds, allAbilities);
            log.info("Icon sync {} finished: {}", kinds, report);
          } catch (RuntimeException e) {
            log.warn("Icon sync {} aborted", kinds, e);
          } finally {
            running.set(false);
          }
        });
    return true;
  }

  public Report run(Set<IconKind> kinds, boolean allAbilities) {
    Run run = new Run();
    try {
      for (IconKind kind : IconKind.values()) {
        if (!kinds.contains(kind)) {
          continue;
        }
        for (IconCandidate candidate : candidates.of(kind, allAbilities)) {
          if (Thread.currentThread().isInterrupted()) {
            return run.report();
          }
          syncOne(candidate, run);
        }
      }
    } finally {
      iconService.refresh();
    }
    return run.report();
  }

  private void syncOne(IconCandidate candidate, Run run) {
    DiscordIconDomain row =
        repo.findByKindAndIconKey(candidate.kind(), candidate.key())
            .orElseGet(
                () ->
                    DiscordIconDomain.builder()
                        .kind(candidate.kind())
                        .iconKey(candidate.key())
                        .createdAt(LocalDateTime.now())
                        .build());
    Path file = fileOf(candidate);
    boolean wasSynced = row.getStatus() == IconStatus.SYNCED;
    if (wasSynced && candidate.imgPath().equals(row.getSourcePath()) && Files.exists(file)) {
      run.unchanged++;
      return;
    }
    boolean touchedDiscord = false;
    try {
      byte[] png =
          IconImages.toSquarePng(
              images.fetch(properties.getCdnUrl() + candidate.imgPath()), properties.getSizePx());
      String hash = IconImages.sha256(png);
      if (wasSynced && hash.equals(row.getImageHash())) {
        store(file, png);
        row.setSourcePath(candidate.imgPath());
        save(row, row.getStatus(), null);
        run.unchanged++;
        return;
      }
      if (!wasSynced && run.inUse >= properties.getMaxEmoji()) {
        save(row, IconStatus.FAILED, "Emoji limit " + properties.getMaxEmoji() + " reached");
        run.overLimit++;
        return;
      }
      store(file, png);
      boolean replacing = wasSynced;
      if (row.getEmojiId() != null) {
        if (replacing) {
          touchedDiscord = true;
          deleteOld(row);
        }
        row.setEmojiId(null);
        row.setEmojiName(null);
        run.used.values().remove(candidate.kind() + ":" + candidate.key());
      }
      touchedDiscord = true;
      String name = freeName(candidate, run);
      EmojiGateway.RemoteEmoji emoji = gateway.create(name, png);
      run.used.put(name, candidate.kind() + ":" + candidate.key());
      row.setEmojiId(emoji.id());
      row.setEmojiName(emoji.name());
      row.setImageHash(hash);
      row.setSourcePath(candidate.imgPath());
      save(row, IconStatus.SYNCED, null);
      if (!wasSynced) {
        run.inUse++;
      }
      if (replacing) {
        run.replaced++;
      } else {
        run.created++;
      }
      pause();
    } catch (IOException | RuntimeException e) {
      recordFailure(row, e, wasSynced && !touchedDiscord);
      run.failed++;
    }
  }

  private void recordFailure(DiscordIconDomain row, Exception e, boolean oldStillWorks) {
    log.warn("Icon {} {} not synced: {}", row.getKind(), row.getIconKey(), e.toString());
    IconStatus status = oldStillWorks ? IconStatus.SYNCED : IconStatus.FAILED;
    if (!oldStillWorks) {
      row.setEmojiId(null);
      row.setEmojiName(null);
    }
    save(row, status, e.toString());
  }

  private String freeName(IconCandidate candidate, Run run) {
    String name = EmojiNames.of(candidate.kind(), candidate.key());
    String owner = candidate.kind() + ":" + candidate.key();
    String holder = run.used.get(name);
    return holder == null || holder.equals(owner)
        ? name
        : EmojiNames.withHash(candidate.kind(), candidate.key());
  }

  private void deleteOld(DiscordIconDomain row) {
    try {
      gateway.delete(row.getEmojiId());
    } catch (RuntimeException e) {
      log.warn(
          "Old emoji {} of {} {} not deleted: {}",
          row.getEmojiId(),
          row.getKind(),
          row.getIconKey(),
          e.toString());
    }
  }

  private void save(DiscordIconDomain row, IconStatus status, String error) {
    row.setStatus(status);
    row.setError(error == null ? null : error.substring(0, Math.min(error.length(), ERROR_LENGTH)));
    row.setUpdatedAt(LocalDateTime.now());
    repo.save(row);
  }

  private Path fileOf(IconCandidate candidate) {
    return properties
        .getDirectory()
        .resolve(candidate.kind().name().toLowerCase(Locale.ROOT))
        .resolve(EmojiNames.of(candidate.kind(), candidate.key()) + ".png");
  }

  private static void store(Path file, byte[] png) throws IOException {
    Files.createDirectories(file.getParent());
    Files.write(file, png);
  }

  private void pause() {
    if (properties.getCreateDelayMillis() <= 0) {
      return;
    }
    try {
      Thread.sleep(properties.getCreateDelayMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private final class Run {
    int created;
    int replaced;
    int unchanged;
    int failed;
    int overLimit;
    long inUse = repo.countByStatus(IconStatus.SYNCED);
    final Map<String, String> used = usedNames();

    Report report() {
      return new Report(created, replaced, unchanged, failed, overLimit);
    }
  }

  private Map<String, String> usedNames() {
    Map<String, String> used = new HashMap<>();
    List<DiscordIconDomain> rows = repo.findAll();
    for (DiscordIconDomain row : rows) {
      if (row.getEmojiName() != null) {
        used.put(row.getEmojiName(), row.getKind() + ":" + row.getIconKey());
      }
    }
    return used;
  }
}
