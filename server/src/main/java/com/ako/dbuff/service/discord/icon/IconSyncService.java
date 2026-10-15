package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
  private final IconRunLock runLock;

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
      IconService iconService,
      IconRunLock runLock) {
    this.properties = properties;
    this.repo = repo;
    this.candidates = candidates;
    this.images = images;
    this.gateway = gateway;
    this.iconService = iconService;
    this.runLock = runLock;
  }

  public record Report(int created, int replaced, int unchanged, int failed, int overLimit) {}

  /** Starts a run in the background; false when one is already running. */
  public boolean start(Set<IconKind> kinds, boolean allAbilities) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    try {
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
    } catch (RuntimeException e) {
      running.set(false);
      throw e;
    }
    return true;
  }

  public Report run(Set<IconKind> kinds, boolean allAbilities) {
    return runLock.exclusively(() -> runLocked(kinds, allAbilities));
  }

  private Report runLocked(Set<IconKind> kinds, boolean allAbilities) {
    Run run = new Run(gateway.list());
    try {
      deleteStale();
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

  private void deleteStale() {
    for (DiscordIconDomain row : repo.findByStaleEmojiIdNotNull()) {
      try {
        gateway.delete(row.getStaleEmojiId());
        row.setStaleEmojiId(null);
        repo.save(row);
      } catch (RuntimeException e) {
        log.warn(
            "Stale emoji {} of {} {} not deleted: {}",
            row.getStaleEmojiId(),
            row.getKind(),
            row.getIconKey(),
            e.toString());
      }
    }
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
      if (run.remote.size() >= properties.getMaxEmoji()) {
        recordOverLimit(row, wasSynced);
        run.overLimit++;
        return;
      }
      store(file, png);
      Long oldId = row.getEmojiId();
      String name = freeName(candidate, row.getEmojiName(), run);
      removeOrphan(name, run);
      EmojiGateway.RemoteEmoji emoji = gateway.create(name, png);
      run.remote.put(emoji.id(), emoji.name());
      run.ownedIds.add(emoji.id());
      run.used.put(name, owner(candidate));
      if (oldId != null && wasSynced) {
        row.setStaleEmojiId(deleteOld(row, oldId, run) ? row.getStaleEmojiId() : oldId);
      }
      row.setEmojiId(emoji.id());
      row.setEmojiName(emoji.name());
      row.setImageHash(hash);
      row.setSourcePath(candidate.imgPath());
      save(row, IconStatus.SYNCED, null);
      if (wasSynced) {
        run.replaced++;
      } else {
        run.created++;
      }
      pause();
    } catch (IOException | RuntimeException e) {
      recordFailure(row, e, wasSynced);
      run.failed++;
    }
  }

  private void recordOverLimit(DiscordIconDomain row, boolean wasSynced) {
    String error = "Emoji limit " + properties.getMaxEmoji() + " reached";
    save(row, wasSynced ? IconStatus.SYNCED : IconStatus.FAILED, error);
  }

  private void recordFailure(DiscordIconDomain row, Exception e, boolean oldStillWorks) {
    log.warn("Icon {} {} not synced: {}", row.getKind(), row.getIconKey(), e.toString());
    if (!oldStillWorks) {
      row.setEmojiId(null);
      row.setEmojiName(null);
    }
    save(row, oldStillWorks ? IconStatus.SYNCED : IconStatus.FAILED, e.toString());
  }

  /** The name for the new emoji; a replacement never reuses the name of the emoji it replaces. */
  private String freeName(IconCandidate candidate, String oldName, Run run) {
    String name = EmojiNames.of(candidate.kind(), candidate.key());
    String hashed = EmojiNames.withHash(candidate.kind(), candidate.key());
    if (name.equals(oldName)) {
      return hashed;
    }
    if (hashed.equals(oldName)) {
      return name;
    }
    String holder = run.used.get(name);
    return holder == null || holder.equals(owner(candidate)) ? name : hashed;
  }

  /** An emoji of that name that no table row owns is a leftover; it is deleted to free the name. */
  private void removeOrphan(String name, Run run) {
    for (Map.Entry<Long, String> remote : new ArrayList<>(run.remote.entrySet())) {
      if (remote.getValue().equals(name) && !run.ownedIds.contains(remote.getKey())) {
        gateway.delete(remote.getKey());
        run.remote.remove(remote.getKey());
      }
    }
  }

  /** Returns true when the old emoji is gone. */
  private boolean deleteOld(DiscordIconDomain row, long oldId, Run run) {
    try {
      gateway.delete(oldId);
      run.remote.remove(oldId);
      run.ownedIds.remove(oldId);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Old emoji {} of {} {} not deleted, kept to retry: {}",
          oldId,
          row.getKind(),
          row.getIconKey(),
          e.toString());
      return false;
    }
  }

  private static String owner(IconCandidate candidate) {
    return candidate.kind() + ":" + candidate.key();
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

  /** What one run knows: the emoji that exist on Discord, which of them the table owns, names. */
  private final class Run {
    int created;
    int replaced;
    int unchanged;
    int failed;
    int overLimit;
    final Map<Long, String> remote = new HashMap<>();
    final Set<Long> ownedIds = new HashSet<>();
    final Map<String, String> used = new HashMap<>();

    Run(List<EmojiGateway.RemoteEmoji> existing) {
      existing.forEach(e -> remote.put(e.id(), e.name()));
      for (DiscordIconDomain row : repo.findAll()) {
        if (row.getEmojiId() != null && row.getStatus() == IconStatus.SYNCED) {
          ownedIds.add(row.getEmojiId());
        }
        if (row.getStaleEmojiId() != null) {
          ownedIds.add(row.getStaleEmojiId());
        }
        if (row.getEmojiName() != null) {
          used.put(row.getEmojiName(), row.getKind() + ":" + row.getIconKey());
        }
      }
    }

    Report report() {
      return new Report(created, replaced, unchanged, failed, overLimit);
    }
  }
}
