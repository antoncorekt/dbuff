package com.ako.dbuff.service.replay;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Reads a decompressed replay. The only implementation that knows the parser library. */
public interface ReplayParser {
  String version();

  /**
   * @param cancelled polled while parsing; when it turns true the parse stops with {@code
   *     FAILED_RESOURCE}
   * @throws ReplayException {@code FAILED_PARSER} when the file cannot be understood, {@code
   *     SKIPPED_NOT_PLAYED} when the game never left the draft or strategy phase
   */
  ReplayFacts parse(Path demoFile, BooleanSupplier cancelled);
}
