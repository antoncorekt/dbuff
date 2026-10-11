package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Removes replay files a crashed or killed JVM left behind. */
@Slf4j
@Component
public class ReplayTempCleaner {
  private final ReplayProcessingProperties properties;
  private final Semaphore parseSemaphore;

  public ReplayTempCleaner(
      ReplayProcessingProperties properties,
      @Qualifier("replayParseSemaphore") Semaphore parseSemaphore) {
    this.properties = properties;
    this.parseSemaphore = parseSemaphore;
  }

  /**
   * Deletes the files in the temp directory that are older than any download and parse can last,
   * and only while no parse is using it, so a file in use is never taken.
   */
  public void sweep() {
    if (parseSemaphore.availablePermits() < properties.getMaxConcurrent()) {
      return;
    }
    Path dir = properties.getTempDir();
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.list(dir)) {
      Instant oldest = Instant.now().minus(maxAge());
      files
          .filter(Files::isRegularFile)
          .filter(f -> isOlderThan(f, oldest))
          .forEach(ReplayTempCleaner::delete);
    } catch (IOException e) {
      log.warn("Could not sweep replay temp directory {}: {}", dir, e.getMessage());
    }
  }

  private Duration maxAge() {
    return Duration.ofSeconds(
            (long) properties.getMaxDownloadSeconds() + properties.getMaxParseSeconds())
        .plusMinutes(10);
  }

  private static boolean isOlderThan(Path file, Instant limit) {
    try {
      return Files.getLastModifiedTime(file).toInstant().isBefore(limit);
    } catch (IOException e) {
      return false;
    }
  }

  private static void delete(Path file) {
    try {
      Files.deleteIfExists(file);
      log.info("Removed leftover replay file {}", file.getFileName());
    } catch (IOException e) {
      log.warn("Could not delete leftover replay file {}: {}", file, e.getMessage());
    }
  }
}
