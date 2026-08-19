package com.ako.dbuff.service.discord.command.adapter;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCommandParserTest {

  @Test
  void parsesDbuffSubcommandWithNoArguments() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!dbuf status");

    assertThat(parsed).isPresent();
    assertThat(parsed.get().alias()).isEqualTo("dbuf");
    assertThat(parsed.get().subcommand()).isEqualTo("status");
    assertThat(parsed.get().arguments()).isEmpty();
  }

  @Test
  void parsesArgumentsAfterTheSubcommand() {
    Optional<TextCommandParser.ParsedCommand> parsed =
        TextCommandParser.parse("!dbuf add-players 111 222");

    assertThat(parsed.get().subcommand()).isEqualTo("add-players");
    assertThat(parsed.get().arguments()).isEqualTo("111 222");
  }

  @Test
  void parsesAliasWithNoSubcommand() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!vs Termit");

    assertThat(parsed.get().alias()).isEqualTo("vs");
    assertThat(parsed.get().subcommand()).isEqualTo("Termit");
    assertThat(parsed.get().arguments()).isEmpty();
  }

  @Test
  void parsesBareAlias() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!rerun");

    assertThat(parsed.get().alias()).isEqualTo("rerun");
    assertThat(parsed.get().subcommand()).isNull();
    assertThat(parsed.get().arguments()).isEmpty();
  }

  @Test
  void isCaseInsensitiveOnTheAliasOnly() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!VS Termit");

    assertThat(parsed.get().alias()).isEqualTo("vs");
    // Argument casing is preserved — player names are case-sensitive to the user.
    assertThat(parsed.get().subcommand()).isEqualTo("Termit");
  }

  @Test
  void toleratesSurroundingAndInternalWhitespace() {
    Optional<TextCommandParser.ParsedCommand> parsed =
        TextCommandParser.parse("   !dbuf   add-players    111   ");

    assertThat(parsed.get().alias()).isEqualTo("dbuf");
    assertThat(parsed.get().subcommand()).isEqualTo("add-players");
    assertThat(parsed.get().arguments()).isEqualTo("111");
  }

  @Test
  void keepsFlagsIntactInTheArguments() {
    Optional<TextCommandParser.ParsedCommand> parsed =
        TextCommandParser.parse("!dbuf register 111 --modes 22 --name Squad");

    assertThat(parsed.get().subcommand()).isEqualTo("register");
    assertThat(parsed.get().arguments()).isEqualTo("111 --modes 22 --name Squad");
  }

  @Test
  void ignoresMessagesWithoutThePrefix() {
    assertThat(TextCommandParser.parse("dbuf status")).isEmpty();
    assertThat(TextCommandParser.parse("hello world")).isEmpty();
  }

  @Test
  void ignoresBarePrefix() {
    assertThat(TextCommandParser.parse("!")).isEmpty();
    assertThat(TextCommandParser.parse("!   ")).isEmpty();
  }

  @Test
  void ignoresNullAndBlank() {
    assertThat(TextCommandParser.parse(null)).isEmpty();
    assertThat(TextCommandParser.parse("")).isEmpty();
  }

  @Test
  void splitToLimit_shortMessageIsOneChunk() {
    assertThat(ThreadAsyncReply.splitToLimit("hello")).containsExactly("hello");
  }

  @Test
  void splitToLimit_prefersSplittingOnNewlines() {
    String line = "x".repeat(1500);
    List<String> chunks = ThreadAsyncReply.splitToLimit(line + "\n" + line);

    assertThat(chunks).hasSize(2);
    assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(2000));
  }

  @Test
  void splitToLimit_hardSplitsASingleOverlongLine() {
    List<String> chunks = ThreadAsyncReply.splitToLimit("y".repeat(4500));

    assertThat(chunks).hasSize(3);
    assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(2000));
    assertThat(String.join("", chunks)).hasSize(4500);
  }
}
