package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayTempCleanerTest {

  @TempDir Path dir;

  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final Semaphore semaphore = new Semaphore(1);

  @Test
  void sweep_removesLeftoversWhenNoParseIsRunning() throws IOException {
    properties.setTempDir(dir);
    old("1-a.replay");
    old("1-b.dem");
    Files.writeString(dir.resolve("2-fresh.replay"), "x");

    new ReplayTempCleaner(properties, semaphore).sweep();

    assertThat(dir).isDirectoryContaining("glob:**/2-fresh.replay");
    assertThat(dir.toFile().list()).containsExactly("2-fresh.replay");
  }

  @Test
  void sweep_leavesFilesAloneWhileAParseHoldsThePermit() throws IOException, InterruptedException {
    properties.setTempDir(dir);
    old("1-b.dem");
    semaphore.acquire();

    new ReplayTempCleaner(properties, semaphore).sweep();

    assertThat(dir.resolve("1-b.dem")).exists();
  }

  private void old(String name) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, "x");
    Files.setLastModifiedTime(
        file,
        java.nio.file.attribute.FileTime.from(
            java.time.Instant.now().minus(java.time.Duration.ofDays(1))));
  }

  @Test
  void sweep_toleratesAMissingDirectory() {
    properties.setTempDir(dir.resolve("never-created"));

    new ReplayTempCleaner(properties, semaphore).sweep();
  }
}
