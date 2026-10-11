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
    Files.writeString(dir.resolve("1-a.replay"), "x");
    Files.writeString(dir.resolve("1-b.dem"), "x");

    new ReplayTempCleaner(properties, semaphore).sweep();

    assertThat(dir).isEmptyDirectory();
  }

  @Test
  void sweep_leavesFilesAloneWhileAParseHoldsThePermit() throws IOException, InterruptedException {
    properties.setTempDir(dir);
    Files.writeString(dir.resolve("1-b.dem"), "x");
    semaphore.acquire();

    new ReplayTempCleaner(properties, semaphore).sweep();

    assertThat(dir.resolve("1-b.dem")).exists();
  }

  @Test
  void sweep_toleratesAMissingDirectory() {
    properties.setTempDir(dir.resolve("never-created"));

    new ReplayTempCleaner(properties, semaphore).sweep();
  }
}
