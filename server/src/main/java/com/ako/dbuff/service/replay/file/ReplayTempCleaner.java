package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  /** Deletes every file in the temp directory, but only while no parse is using it. */
  public void sweep() {
    if (parseSemaphore.availablePermits() < properties.getMaxConcurrent()) {
      return;
    }
    Path dir = properties.getTempDir();
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.list(dir)) {
      files.filter(Files::isRegularFile).forEach(ReplayTempCleaner::delete);
    } catch (IOException e) {
      log.warn("Could not sweep replay temp directory {}: {}", dir, e.getMessage());
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
