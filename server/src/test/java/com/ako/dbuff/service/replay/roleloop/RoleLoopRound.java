package com.ako.dbuff.service.replay.roleloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** The report files of one round, {@code <base>/round-NN/<matchId>.md}. */
final class RoleLoopRound {
  private static final String PREFIX = "round-";

  private final Path base;
  private final int number;

  RoleLoopRound(Path base, int number) {
    this.base = base;
    this.number = number;
  }

  /**
   * The requested round, else the one after the last that exists.
   *
   * @param requested the {@code ROUND} text, null or blank for the next free round
   * @param overwrite whether an explicit round that already has reports may be written over
   */
  static RoleLoopRound open(Path base, String requested, boolean overwrite) {
    if (requested == null || requested.isBlank()) {
      return new RoleLoopRound(base, last(base).orElse(0) + 1);
    }
    int number;
    try {
      number = Integer.parseInt(requested.strip());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "ROUND must be a positive whole number, got '" + requested + "'");
    }
    if (number < 1) {
      throw new IllegalArgumentException("ROUND must be a positive whole number, got " + number);
    }
    RoleLoopRound round = new RoleLoopRound(base, number);
    if (!overwrite && round.hasReports()) {
      throw new IllegalStateException(
          "%s already has reports; choose another ROUND or set ROUND_OVERWRITE=1"
              .formatted(round.dir()));
    }
    return round;
  }

  boolean hasReports() {
    if (!Files.isDirectory(dir())) {
      return false;
    }
    try (Stream<Path> files = Files.list(dir())) {
      return files.anyMatch(f -> f.getFileName().toString().endsWith(".md"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int number() {
    return number;
  }

  Path dir() {
    return base.resolve(String.format("%s%02d", PREFIX, number));
  }

  Path write(String fileName, String content) {
    try {
      Files.createDirectories(dir());
      return Files.writeString(dir().resolve(fileName), content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The file of the latest earlier round that has it and that {@code usable} accepts. */
  Optional<String> previous(String fileName, Predicate<String> usable) {
    return rounds(base)
        .filter(n -> n < number)
        .boxed()
        .sorted((a, b) -> b - a)
        .map(n -> new RoleLoopRound(base, n).dir().resolve(fileName))
        .filter(Files::exists)
        .map(RoleLoopRound::read)
        .filter(usable)
        .findFirst();
  }

  private static OptionalInt last(Path base) {
    return rounds(base).max();
  }

  private static IntStream rounds(Path base) {
    if (!Files.isDirectory(base)) {
      return IntStream.empty();
    }
    try (Stream<Path> dirs = Files.list(base)) {
      return IntStream.of(
          dirs.map(d -> d.getFileName().toString())
              .filter(name -> name.matches(PREFIX + "\\d+"))
              .mapToInt(name -> Integer.parseInt(name.substring(PREFIX.length())))
              .toArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
