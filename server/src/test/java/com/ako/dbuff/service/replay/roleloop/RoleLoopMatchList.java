package com.ako.dbuff.service.replay.roleloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The match ids of the review list: one per line, blank lines and {@code #} comments ignored. */
final class RoleLoopMatchList {
  private RoleLoopMatchList() {}

  static List<Long> read(Path file) {
    try {
      return parse(Files.readAllLines(file), file.getFileName().toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Long> parse(List<String> lines, String source) {
    Set<Long> ids = new LinkedHashSet<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String text = (line.contains("#") ? line.substring(0, line.indexOf('#')) : line).strip();
      if (text.isEmpty()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(text));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "%s line %d: not a match id: '%s'".formatted(source, i + 1, line));
      }
    }
    return new ArrayList<>(ids);
  }
}
