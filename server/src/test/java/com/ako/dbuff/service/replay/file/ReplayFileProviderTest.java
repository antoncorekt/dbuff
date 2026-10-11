package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplaySource;
import com.github.luben.zstd.Zstd;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayFileProviderTest {

  private static final byte[] DEMO = "PBDEMS2 pretend replay content".repeat(200).getBytes();

  @TempDir Path tempDir;

  private HttpServer server;
  private final AtomicInteger status = new AtomicInteger(200);
  private volatile byte[] body = new byte[0];
  private volatile boolean stall;
  private ReplayProcessingProperties properties;
  private ReplayFileProvider provider;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(
              status.get(), stall ? 1_000_000 : body.length == 0 ? -1 : body.length);
          if (stall) {
            try {
              Thread.sleep(5_000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          } else if (body.length > 0) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    properties = new ReplayProcessingProperties();
    properties.setTempDir(tempDir.resolve("replays"));
    properties.setUrlTemplate(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/%d/%d_%d.dem.bz2");
    provider =
        new ReplayFileProvider(
            new HttpReplayDownloader(properties),
            List.of(new ZstdDecompressor(), new Bzip2Decompressor()),
            properties);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void open_unpacksZstdAndBzip2WhateverTheUrlSays() throws IOException {
    body = Zstd.compress(DEMO);
    try (ReplayFile file = provider.open(source())) {
      assertThat(Files.readAllBytes(file.path())).isEqualTo(DEMO);
      assertThat(file.compressedBytes()).isEqualTo(body.length);
    }

    body = bzip2(DEMO);
    try (ReplayFile file = provider.open(source())) {
      assertThat(Files.readAllBytes(file.path())).isEqualTo(DEMO);
    }
  }

  @Test
  void close_deletesTheReplayAndNothingElseIsLeftBehind() throws IOException {
    body = Zstd.compress(DEMO);

    ReplayFile file = provider.open(source());
    file.close();

    assertThat(file.path()).doesNotExist();
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  @Test
  void open_treatsAMissingReplayAsExpired() {
    status.set(404);

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.EXPIRED);
    status.set(410);
    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.EXPIRED);
  }

  @Test
  void open_treatsServerErrorsAndThrottlingAsTemporary() {
    for (int code : new int[] {403, 429, 500, 503}) {
      status.set(code);
      assertThat(statusOfFailure()).as("HTTP %d", code).isEqualTo(ReplayStatus.FAILED_DOWNLOAD);
    }
  }

  @Test
  void open_rejectsABodyThatIsNotAReplayAndCleansUp() throws IOException {
    body = "<html>Service unavailable</html>".getBytes(StandardCharsets.UTF_8);

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.FAILED_DOWNLOAD);
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  @Test
  void open_rejectsATruncatedArchiveAndCleansUp() throws IOException {
    byte[] whole = Zstd.compress(DEMO);
    body = java.util.Arrays.copyOf(whole, whole.length / 2);

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.FAILED_DOWNLOAD);
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  @Test
  void open_cutsOffABodyThatStallsAfterTheHeaders() throws IOException {
    properties.setMaxDownloadSeconds(1);
    stall = true;

    long started = System.nanoTime();
    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.FAILED_DOWNLOAD);

    assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(4_000);
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  @Test
  void open_rejectsAnEmptyBody() {
    body = new byte[0];

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.FAILED_DOWNLOAD);
  }

  @Test
  void open_refusesADownloadOverTheSizeLimit() throws IOException {
    properties.setMaxReplayBytes(DataSize.ofBytes(20));
    body = Zstd.compress(DEMO);

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.TOO_LARGE);
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  @Test
  void open_refusesAReplayThatUnpacksPastTheLimit() throws IOException {
    properties.setMaxDemoBytes(DataSize.ofBytes(1000));
    body = Zstd.compress(DEMO);

    assertThat(statusOfFailure()).isEqualTo(ReplayStatus.TOO_LARGE);
    assertThat(filesIn(properties.getTempDir())).isEmpty();
  }

  private ReplayStatus statusOfFailure() {
    ReplayStatus[] result = new ReplayStatus[1];
    assertThatThrownBy(() -> provider.open(source()))
        .isInstanceOfSatisfying(ReplayException.class, e -> result[0] = e.getStatus());
    return result[0];
  }

  private static ReplaySource source() {
    return new ReplaySource(9038725154L, 7L, 123L);
  }

  private static byte[] bzip2(byte[] data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (BZip2CompressorOutputStream bz = new BZip2CompressorOutputStream(out)) {
      bz.write(data);
    }
    return out.toByteArray();
  }

  private static List<Path> filesIn(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.toList();
    }
  }
}
