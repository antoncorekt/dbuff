package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplaySource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpReplayDownloader implements ReplayDownloader {
  private static final int NOT_FOUND = 404;
  private static final int GONE = 410;
  private static final int OK = 200;

  private final ReplayProcessingProperties properties;
  private final HttpClient client;

  public HttpReplayDownloader(ReplayProcessingProperties properties) {
    this.properties = properties;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public Path download(ReplaySource source, Path targetDir) {
    String url =
        String.format(
            Locale.ROOT,
            properties.getUrlTemplate(),
            source.cluster(),
            source.matchId(),
            source.salt());
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
            .GET()
            .build();
    Path target = null;
    try {
      Files.createDirectories(targetDir);
      target = Files.createTempFile(targetDir, source.matchId() + "-", ".replay");
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        checkStatus(response.statusCode(), source);
        checkDeclaredSize(response, source);
        try (Watchdog watchdog = new Watchdog(body, properties.getMaxDownloadSeconds())) {
          copyLimited(body, target, source);
          if (!watchdog.finishedInTime()) {
            throw new IOException("Download exceeded " + properties.getMaxDownloadSeconds() + " s");
          }
        }
      }
      return target;
    } catch (ReplayException e) {
      deleteQuietly(target);
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      deleteQuietly(target);
      throw new ReplayException(ReplayStatus.FAILED_DOWNLOAD, "Download interrupted", e);
    } catch (IOException | RuntimeException e) {
      deleteQuietly(target);
      throw new ReplayException(
          ReplayStatus.FAILED_DOWNLOAD,
          "Download of match " + source.matchId() + " failed: " + e.getMessage(),
          e);
    }
  }

  private static void checkStatus(int code, ReplaySource source) {
    if (code == OK) {
      return;
    }
    if (code == NOT_FOUND || code == GONE) {
      throw new ReplayException(
          ReplayStatus.EXPIRED, "Replay of match " + source.matchId() + " is gone (" + code + ")");
    }
    throw new ReplayException(
        ReplayStatus.FAILED_DOWNLOAD,
        "Replay server answered " + code + " for match " + source.matchId());
  }

  private void checkDeclaredSize(HttpResponse<?> response, ReplaySource source) {
    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if (declared > properties.getMaxReplayBytes().toBytes()) {
      throw new ReplayException(
          ReplayStatus.TOO_LARGE,
          "Replay of match " + source.matchId() + " declares " + declared + " bytes");
    }
  }

  private void copyLimited(InputStream in, Path target, ReplaySource source) throws IOException {
    try (OutputStream out = Files.newOutputStream(target)) {
      LimitedCopier.copy(
          in,
          out,
          properties.getMaxReplayBytes().toBytes(),
          limit ->
              new ReplayException(
                  ReplayStatus.TOO_LARGE,
                  "Replay of match " + source.matchId() + " is over " + limit + " bytes"));
    }
  }

  /**
   * The request timeout only covers the headers, so a body that stalls would block the read
   * forever. Closing the stream after the deadline makes that read fail.
   */
  private static final class Watchdog implements AutoCloseable {
    private final Thread thread;
    private final AtomicBoolean settled = new AtomicBoolean();

    Watchdog(InputStream body, int seconds) {
      thread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      Thread.sleep(Duration.ofSeconds(seconds));
                      if (settled.compareAndSet(false, true)) {
                        body.close();
                      }
                    } catch (InterruptedException e) {
                      // The download finished first.
                    } catch (IOException e) {
                      log.debug("Closing a stalled download failed: {}", e.getMessage());
                    }
                  });
    }

    /** True when the copy ended before the deadline; false when the watchdog got there first. */
    boolean finishedInTime() {
      return settled.compareAndSet(false, true);
    }

    @Override
    public void close() {
      thread.interrupt();
    }
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not delete temp replay file {}: {}", path, e.getMessage());
    }
  }
}
