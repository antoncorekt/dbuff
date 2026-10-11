package com.ako.dbuff.service.replay.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/** A decompressed replay on disk; closing it deletes the file. */
@Slf4j
public record ReplayFile(Path path, long compressedBytes) implements AutoCloseable {
  @Override
  public void close() {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not delete replay file {}: {}", path, e.getMessage());
    }
  }
}
