package com.ako.dbuff.service.replay.roleloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The match ids of the review list: one per line, blank lines and {@code #} comments ignored. */
final class RoleLoopMatchList {
  private RoleLoopMatchList() {}

  static List<Long> read(Path file) {
    try {
      return parse(Files.readAllLines(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Long> parse(List<String> lines) {
    return lines.stream()
        .map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .map(Long::parseLong)
        .distinct()
        .toList();
  }
}
