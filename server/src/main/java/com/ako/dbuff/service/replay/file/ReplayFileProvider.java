package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplaySource;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Turns a replay source into a decompressed file, whichever format Valve served it in. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayFileProvider {
  private static final int HEADER_BYTES = 4;

  private final ReplayDownloader downloader;
  private final List<ReplayDecompressor> decompressors;
  private final ReplayProcessingProperties properties;

  public ReplayFile open(ReplaySource source) {
    Path dir = properties.getTempDir();
    Path compressed = downloader.download(source, dir);
    try {
      long compressedBytes = Files.size(compressed);
      Path demo = decompress(compressed, dir, source);
      return new ReplayFile(demo, compressedBytes);
    } catch (IOException e) {
      throw new ReplayException(
          ReplayStatus.FAILED_DOWNLOAD, "Could not read downloaded replay: " + e.getMessage(), e);
    } finally {
      deleteQuietly(compressed);
    }
  }

  private Path decompress(Path compressed, Path dir, ReplaySource source) throws IOException {
    ReplayDecompressor decompressor = pickDecompressor(compressed);
    Path demo = Files.createTempFile(dir, source.matchId() + "-", ".dem");
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(compressed));
        InputStream in = decompressor.open(raw);
        OutputStream out = Files.newOutputStream(demo)) {
      copyLimited(in, out, source);
      return demo;
    } catch (IOException e) {
      deleteQuietly(demo);
      throw new ReplayException(
          ReplayStatus.FAILED_DOWNLOAD,
          "Replay of match " + source.matchId() + " is corrupt: " + e.getMessage(),
          e);
    } catch (ReplayException e) {
      deleteQuietly(demo);
      throw e;
    }
  }

  private ReplayDecompressor pickDecompressor(Path compressed) throws IOException {
    byte[] header = new byte[HEADER_BYTES];
    int read;
    try (InputStream in = Files.newInputStream(compressed)) {
      read = in.readNBytes(header, 0, HEADER_BYTES);
    }
    byte[] actual = Arrays.copyOf(header, read);
    return decompressors.stream()
        .filter(d -> d.supports(actual))
        .findFirst()
        .orElseThrow(
            () ->
                new ReplayException(
                    ReplayStatus.FAILED_DOWNLOAD, "Downloaded file is not a known replay format"));
  }

  private void copyLimited(InputStream in, OutputStream out, ReplaySource source)
      throws IOException {
    long limit = properties.getMaxDemoBytes().toBytes();
    long total = 0;
    byte[] buffer = new byte[64 * 1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > limit) {
        throw new ReplayException(
            ReplayStatus.TOO_LARGE,
            "Replay of match " + source.matchId() + " unpacks past " + limit + " bytes");
      }
      out.write(buffer, 0, read);
    }
    if (total == 0) {
      throw new ReplayException(ReplayStatus.FAILED_DOWNLOAD, "Downloaded replay is empty");
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not delete temp file {}: {}", path, e.getMessage());
    }
  }
}
