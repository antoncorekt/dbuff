package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.service.replay.ReplaySource;
import java.nio.file.Path;

/** Fetches the compressed replay of one match. */
public interface ReplayDownloader {
  /**
   * Streams the replay into {@code targetDir} and returns the file.
   *
   * @throws com.ako.dbuff.service.replay.ReplayException {@code EXPIRED} when Valve no longer has
   *     it, {@code TOO_LARGE} past the size limit, {@code FAILED_DOWNLOAD} for anything temporary
   */
  Path download(ReplaySource source, Path targetDir);
}
