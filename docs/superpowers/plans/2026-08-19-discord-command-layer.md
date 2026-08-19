# Discord Command Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace DBuff's prefix-based Discord commands with discoverable slash commands that autocomplete their arguments, add `/stats` player statistics commands, and keep the existing `!` commands working as aliases.

**Architecture:** Handlers are written once against a normalized `CommandContext` and fed by two thin adapters — one for slash interactions, one for text messages. Every command acknowledges synchronously, creates a thread, then fills that thread from a virtual thread. Autocomplete providers read the in-memory constant caches.

**Tech Stack:** JDA 6.3.0, Spring Boot 3.5, Java 21 virtual threads, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-discord-friendly-commands-design.md`

**This is plan 2 of 2.** It requires `docs/superpowers/plans/2026-08-19-stats-data-layer.md` to be fully executed first — every `/stats` command calls methods that plan adds.

---

## Before You Start

**Verify the JDA API surface.** This plan was written against the JDA 6.3.0
documentation but not compiled. Method names in JDA's interaction and thread APIs
shifted between v4, v5, and v6. Before Task 1, confirm these exist with these
signatures, and adjust the plan's code if not:

```java
net.dv8tion.jda.api.events.session.ReadyEvent
net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
net.dv8tion.jda.api.interactions.commands.build.Commands#slash(String, String)
net.dv8tion.jda.api.interactions.commands.build.SubcommandData
net.dv8tion.jda.api.interactions.commands.build.OptionData
net.dv8tion.jda.api.interactions.commands.Command.Choice
net.dv8tion.jda.api.interactions.InteractionHook#retrieveOriginal()
net.dv8tion.jda.api.entities.Message#createThreadChannel(String)
net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration
```

Run `./gradlew :server:dependencies --configuration compileClasspath | grep JDA`
to confirm the version, then check the javadoc for that exact release. **Do not
guess** — a wrong method name here costs an hour of confusing compile errors
across fifteen files.

## Conventions

- Spring beans: `@Slf4j @Component @RequiredArgsConstructor`, `final` fields.
- Listeners extend `net.dv8tion.jda.api.hooks.ListenerAdapter`.
- Existing listeners use `@Lazy` on injected services to break circular
  dependencies with `MatchReportOrchestrator` — keep doing that where the same
  cycle would appear.
- Tests: JUnit 5, Mockito, AssertJ static imports.
- **Always** `./gradlew spotlessApply` before committing.
- Run one test class: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.CommandRegistryTest"`

## Hard Discord Limits

These are protocol constraints, not tunable values. Violating them produces
runtime errors from Discord, not compile errors.

| Limit | Value |
|---|---|
| Time to acknowledge an interaction | **3 seconds** |
| Autocomplete choices per response | **25** |
| Autocomplete choice value length | **100 characters** |
| Thread name length | **100 characters** |
| Message content length | **2000 characters** |
| Embed fields per embed | **25** |
| Ephemeral replies can have threads | **No** |
| Threads can nest | **No** |

---

## Task 1: Core interfaces and the test double

The foundation every later task builds on. `AsyncReply` is what makes handlers
testable without a Discord connection — the payoff for the whole design.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/AsyncReply.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/CommandContext.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/DbuffCommand.java`
- Create: `server/src/test/java/com/ako/dbuff/service/discord/command/FakeCommandContext.java`
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/FakeCommandContextTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/FakeCommandContextTest.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the test double itself, so later handler tests can trust it. */
class FakeCommandContextTest {

  @Test
  void acknowledge_recordsSummaryAndThreadName() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("Scouting Termit…", "stats: Termit");

    assertThat(context.getAcknowledgeSummary()).isEqualTo("Scouting Termit…");
    assertThat(context.getAcknowledgeThreadName()).isEqualTo("stats: Termit");
    assertThat(reply).isNotNull();
  }

  @Test
  void asyncReply_capturesPostsInOrder() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.post("first");
    reply.post("second");

    assertThat(context.getPosts()).containsExactly("first", "second");
  }

  @Test
  void asyncReply_capturesFailuresSeparatelyFromPosts() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.post("progress");
    reply.fail("boom");

    assertThat(context.getPosts()).containsExactly("progress");
    assertThat(context.getFailures()).containsExactly("boom");
  }

  @Test
  void replyEphemeral_isCapturedAndDoesNotCreateAThread() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    context.replyEphemeral("No instance registered.");

    assertThat(context.getEphemeralReplies()).containsExactly("No instance registered.");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void options_areReadableByName() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Termit")
            .option("period", "last_7_days")
            .build();

    assertThat(context.getOption("player")).isEqualTo("Termit");
    assertThat(context.getOption("period")).isEqualTo("last_7_days");
    assertThat(context.getOption("absent")).isNull();
  }

  @Test
  void getOptionAsList_splitsOnCommasAndTrims() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("items", "blink, black_king_bar ,manta").build();

    assertThat(context.getOptionAsList("items"))
        .containsExactly("blink", "black_king_bar", "manta");
  }

  @Test
  void getOptionAsList_absentOption_isEmptyList() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    assertThat(context.getOptionAsList("items")).isEmpty();
  }

  @Test
  void getOptionAsList_blankEntriesAreDropped() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("items", "blink,, ,manta").build();

    assertThat(context.getOptionAsList("items")).containsExactly("blink", "manta");
  }

  @Test
  void getOptionAsInt_parsesOrReturnsDefault() {
    FakeCommandContext context = FakeCommandContext.builder().option("limit", "15").build();

    assertThat(context.getOptionAsInt("limit", 10)).isEqualTo(15);
    assertThat(context.getOptionAsInt("absent", 10)).isEqualTo(10);
  }

  @Test
  void getOptionAsInt_unparseableValue_returnsDefault() {
    FakeCommandContext context = FakeCommandContext.builder().option("limit", "abc").build();

    assertThat(context.getOptionAsInt("limit", 10)).isEqualTo(10);
  }

  @Test
  void insideThread_isReportedFromTheBuilder() {
    assertThat(FakeCommandContext.builder().insideThread(true).build().isInsideThread()).isTrue();
    assertThat(FakeCommandContext.builder().build().isInsideThread()).isFalse();
  }

  @Test
  void postEmbedLines_capturesEachLine() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    AsyncReply reply = context.acknowledge("working", "thread");
    reply.postLines(List.of("line one", "line two"));

    assertThat(context.getPosts()).containsExactly("line one", "line two");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.FakeCommandContextTest"`

Expected: **compilation failure** — `cannot find symbol: class FakeCommandContext`.

- [ ] **Step 3: Write `AsyncReply`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/AsyncReply.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Where a command writes its results after acknowledging.
 *
 * <p>Obtained from {@link CommandContext#acknowledge}. Backed by a freshly created thread when the
 * command was invoked in a text channel, or by the current thread when it was invoked inside one —
 * handlers do not need to know which.
 *
 * <p>Implementations are used from a virtual thread and may block.
 */
public interface AsyncReply {

  /** Posts a plain message. Content over 2000 characters must be split by the caller. */
  void post(String message);

  /** Posts each message in order. Convenience for formatters that return pre-split chunks. */
  default void postLines(List<String> messages) {
    messages.forEach(this::post);
  }

  /** Posts an embed. */
  void postEmbed(MessageEmbed embed);

  /**
   * Reports that the command failed after acknowledging. Distinct from {@link
   * CommandContext#replyEphemeral} because by this point a thread exists and the user is watching
   * it — an ephemeral reply would be invisible there.
   */
  void fail(String message);
}
```

- [ ] **Step 4: Write `CommandContext`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/CommandContext.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import java.util.Optional;

/**
 * Everything a command handler needs, normalized across the slash and text surfaces.
 *
 * <p>The two-phase reply model is deliberate. Discord discards an interaction that is not
 * acknowledged within three seconds, and every interesting command here (statistics aggregations,
 * OCR, OpenDota fetches) can exceed that. So handlers validate first, {@link #acknowledge} second,
 * and do the slow work third — writing results into the {@link AsyncReply} they got back.
 *
 * <p>Validation failures therefore use {@link #replyEphemeral}, which must be called <em>before</em>
 * acknowledging: Discord does not allow a thread on an ephemeral message.
 */
public interface CommandContext {

  /** Raw option value, or null when absent. */
  String getOption(String name);

  /**
   * A comma-separated option split into trimmed, non-blank entries. Empty list when absent.
   *
   * <p>This is how list-valued options arrive, because Discord options are single-valued.
   */
  List<String> getOptionAsList(String name);

  /** Integer option, falling back to {@code defaultValue} when absent or unparseable. */
  int getOptionAsInt(String name, int defaultValue);

  /** Discord user snowflake for a user-typed option, or empty when absent. */
  Optional<String> getOptionAsUserId(String name);

  /** Snowflake of the user who invoked the command. */
  String getInvokerId();

  /** Channel the command was invoked in. For a thread, the thread's own ID. */
  String getChannelId();

  /** Parent text channel ID — the thread's parent when inside a thread, else the channel itself. */
  String getParentChannelId();

  /** Guild ID, or empty in a direct message. */
  Optional<String> getGuildId();

  /** True when invoked inside a thread, where a new thread cannot be created. */
  boolean isInsideThread();

  /** Thread name when invoked inside a thread, else empty. Used by {@code /match} to find its ID. */
  Optional<String> getThreadName();

  /**
   * Acknowledges the command visibly and returns the sink for its results.
   *
   * @param summary short public message confirming the command was accepted
   * @param threadName name for the created thread; truncated to Discord's 100-character limit
   */
  AsyncReply acknowledge(String summary, String threadName);

  /**
   * Replies privately to the invoker without creating a thread. For validation failures only — must
   * be called before {@link #acknowledge}.
   */
  void replyEphemeral(String message);
}
```

- [ ] **Step 5: Write `DbuffCommand`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/DbuffCommand.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * One top-level Discord command, e.g. {@code /stats}, including all its subcommands.
 *
 * <p>Implementations are Spring beans collected by {@link CommandRegistry}. They must not touch JDA
 * directly — everything goes through {@link CommandContext}, which is what allows them to be tested
 * without a Discord connection.
 */
public interface DbuffCommand {

  /** Root command name without the leading slash, e.g. {@code stats}. */
  String getName();

  /** Full JDA definition, used both for registration and for generating help. */
  SlashCommandData getDefinition();

  /**
   * Text-command aliases that route here, without the {@code !} prefix, e.g. {@code vs}. Empty when
   * the command is slash-only.
   */
  default List<String> getTextAliases() {
    return List.of();
  }

  /**
   * Runs the command.
   *
   * @param subcommand the invoked subcommand name, or null for a root-level invocation
   * @param context normalized arguments and reply sinks
   */
  void execute(String subcommand, CommandContext context);
}
```

- [ ] **Step 6: Write `FakeCommandContext`**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/FakeCommandContext.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * In-memory {@link CommandContext} for handler tests. Captures everything a handler says instead of
 * sending it to Discord.
 */
@Getter
public class FakeCommandContext implements CommandContext {

  private final Map<String, String> options;
  private final String invokerId;
  private final String channelId;
  private final String parentChannelId;
  private final String guildId;
  private final boolean insideThread;
  private final String threadName;

  private final List<String> posts = new ArrayList<>();
  private final List<MessageEmbed> embeds = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private final List<String> ephemeralReplies = new ArrayList<>();

  private String acknowledgeSummary;
  private String acknowledgeThreadName;

  private FakeCommandContext(Builder builder) {
    this.options = builder.options;
    this.invokerId = builder.invokerId;
    this.channelId = builder.channelId;
    this.parentChannelId = builder.parentChannelId;
    this.guildId = builder.guildId;
    this.insideThread = builder.insideThread;
    this.threadName = builder.threadName;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String getOption(String name) {
    return options.get(name);
  }

  @Override
  public List<String> getOptionAsList(String name) {
    String raw = options.get(name);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public int getOptionAsInt(String name, int defaultValue) {
    String raw = options.get(name);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public Optional<String> getOptionAsUserId(String name) {
    return Optional.ofNullable(options.get(name));
  }

  @Override
  public Optional<String> getGuildId() {
    return Optional.ofNullable(guildId);
  }

  @Override
  public Optional<String> getThreadName() {
    return Optional.ofNullable(threadName);
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    this.acknowledgeSummary = summary;
    this.acknowledgeThreadName = threadName;
    return new AsyncReply() {
      @Override
      public void post(String message) {
        posts.add(message);
      }

      @Override
      public void postEmbed(MessageEmbed embed) {
        embeds.add(embed);
      }

      @Override
      public void fail(String message) {
        failures.add(message);
      }
    };
  }

  @Override
  public void replyEphemeral(String message) {
    ephemeralReplies.add(message);
  }

  /** Fluent builder. All fields have sensible defaults so tests set only what they care about. */
  public static class Builder {
    private final Map<String, String> options = new HashMap<>();
    private String invokerId = "invoker-1";
    private String channelId = "channel-1";
    private String parentChannelId = "channel-1";
    private String guildId = "guild-1";
    private boolean insideThread = false;
    private String threadName = null;

    public Builder option(String name, String value) {
      options.put(name, value);
      return this;
    }

    public Builder invokerId(String invokerId) {
      this.invokerId = invokerId;
      return this;
    }

    public Builder channelId(String channelId) {
      this.channelId = channelId;
      return this;
    }

    public Builder parentChannelId(String parentChannelId) {
      this.parentChannelId = parentChannelId;
      return this;
    }

    public Builder guildId(String guildId) {
      this.guildId = guildId;
      return this;
    }

    public Builder insideThread(boolean insideThread) {
      this.insideThread = insideThread;
      return this;
    }

    public Builder threadName(String threadName) {
      this.threadName = threadName;
      this.insideThread = true;
      return this;
    }

    public FakeCommandContext build() {
      return new FakeCommandContext(this);
    }
  }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.FakeCommandContextTest"`

Expected: **PASS**, 12 tests.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/discord/command/ \
        server/src/test/java/com/ako/dbuff/service/discord/command/
git commit -m "feat: add Discord command context abstraction and test double"
```

---

## Task 2: `TextSimilarity` utility and `CommandRegistry`

The registry is the single source of truth for what commands exist. Help text is
generated from it, so help can never drift from reality — the failure mode that
left `MatchSummaryDiscordListener` dead and unnoticed.

This task also extracts the Levenshtein helper that plan 1 buried inside
`ConstantNameResolver`, because the registry needs the same "did you mean" logic
for mistyped subcommands.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/TextSimilarity.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/CommandRegistry.java`
- Modify: `server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java` — delegate to the utility
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/TextSimilarityTest.java`
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/CommandRegistryTest.java`

- [ ] **Step 1: Write the failing test for `TextSimilarity`**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/TextSimilarityTest.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSimilarityTest {

  @Test
  void editDistance_identicalStrings_isZero() {
    assertThat(TextSimilarity.editDistance("blink", "blink")).isZero();
  }

  @Test
  void editDistance_singleSubstitution_isOne() {
    assertThat(TextSimilarity.editDistance("blink", "blonk")).isEqualTo(1);
  }

  @Test
  void editDistance_singleDeletion_isOne() {
    assertThat(TextSimilarity.editDistance("blink", "blnk")).isEqualTo(1);
  }

  @Test
  void editDistance_emptyAgainstNonEmpty_isLength() {
    assertThat(TextSimilarity.editDistance("", "blink")).isEqualTo(5);
    assertThat(TextSimilarity.editDistance("blink", "")).isEqualTo(5);
  }

  @Test
  void closest_findsTheNearestCandidate() {
    List<String> candidates = List.of("add-players", "remove-players", "status");

    assertThat(TextSimilarity.closest("add-player", candidates, 4)).contains("add-players");
    assertThat(TextSimilarity.closest("statu", candidates, 4)).contains("status");
  }

  @Test
  void closest_isCaseInsensitive() {
    assertThat(TextSimilarity.closest("STATUS", List.of("status"), 4)).contains("status");
  }

  @Test
  void closest_beyondMaxDistance_isEmpty() {
    assertThat(TextSimilarity.closest("zzzzzzzzzz", List.of("status"), 4)).isEmpty();
  }

  @Test
  void closest_emptyCandidates_isEmpty() {
    assertThat(TextSimilarity.closest("status", List.of(), 4)).isEmpty();
  }

  @Test
  void closest_nullOrBlankInput_isEmpty() {
    assertThat(TextSimilarity.closest(null, List.of("status"), 4)).isEmpty();
    assertThat(TextSimilarity.closest("   ", List.of("status"), 4)).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.TextSimilarityTest"`

Expected: **compilation failure** — `cannot find symbol: class TextSimilarity`.

- [ ] **Step 3: Write `TextSimilarity`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/TextSimilarity.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.Collection;
import java.util.Optional;

/** Levenshtein distance and nearest-match lookup, for "did you mean" suggestions. */
public final class TextSimilarity {

  private TextSimilarity() {}

  /**
   * The nearest candidate to {@code input}, or empty when nothing is within {@code maxDistance}.
   * Comparison is case-insensitive; the returned string preserves the candidate's original casing.
   */
  public static Optional<String> closest(
      String input, Collection<String> candidates, int maxDistance) {
    if (input == null || input.isBlank() || candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }
    String needle = input.trim().toLowerCase();

    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      int distance = editDistance(needle, candidate.toLowerCase());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return bestDistance <= maxDistance ? Optional.ofNullable(best) : Optional.empty();
  }

  /** Standard Levenshtein distance, two-row variant. */
  public static int editDistance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];

    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
```

- [ ] **Step 4: Remove the duplicate from `ConstantNameResolver`**

In `server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java`
delete the private `editDistance` method and the private `closest` method, and
replace the three `suggestX` bodies with delegations:

```java
  public Optional<String> suggestItem(String unknown) {
    return TextSimilarity.closest(unknown, itemDisplayNames(), MAX_SUGGESTION_DISTANCE);
  }

  public Optional<String> suggestHero(String unknown) {
    return TextSimilarity.closest(unknown, heroDisplayNames(), MAX_SUGGESTION_DISTANCE);
  }

  public Optional<String> suggestAbility(String unknown) {
    return TextSimilarity.closest(unknown, abilityNames(), MAX_SUGGESTION_DISTANCE);
  }
```

Add `import com.ako.dbuff.service.discord.command.TextSimilarity;`.

Run `./gradlew :server:test --tests "com.ako.dbuff.service.constant.ConstantNameResolverTest"`
and confirm it still passes — the suggestion tests from plan 1 Task 2 must be
unaffected by this refactor.

- [ ] **Step 5: Write the failing test for `CommandRegistry`**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/CommandRegistryTest.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryTest {

  private StubCommand stats;
  private StubCommand scout;
  private CommandRegistry registry;

  @BeforeEach
  void setUp() {
    stats =
        new StubCommand(
            "stats",
            Commands.slash("stats", "Player statistics")
                .addSubcommands(
                    new SubcommandData("overall", "Overall performance"),
                    new SubcommandData("items", "Item builds")),
            List.of());
    scout = new StubCommand("scout", Commands.slash("scout", "Scout an opponent"), List.of("vs"));

    registry = new CommandRegistry(List.of(stats, scout));
  }

  @Test
  void findByName_returnsTheCommand() {
    assertThat(registry.findByName("stats")).contains(stats);
    assertThat(registry.findByName("scout")).contains(scout);
  }

  @Test
  void findByName_isCaseInsensitive() {
    assertThat(registry.findByName("STATS")).contains(stats);
  }

  @Test
  void findByName_unknown_isEmpty() {
    assertThat(registry.findByName("nope")).isEmpty();
  }

  @Test
  void findByTextAlias_resolvesAliasesAndRootNames() {
    assertThat(registry.findByTextAlias("vs")).contains(scout);
    assertThat(registry.findByTextAlias("stats")).contains(stats);
  }

  @Test
  void findByTextAlias_unknown_isEmpty() {
    assertThat(registry.findByTextAlias("nope")).isEmpty();
  }

  @Test
  void getDefinitions_returnsOnePerCommand() {
    assertThat(registry.getDefinitions()).hasSize(2);
  }

  @Test
  void suggestCommand_offersTheNearestName() {
    assertThat(registry.suggestCommand("stat")).contains("stats");
    assertThat(registry.suggestCommand("scot")).contains("scout");
  }

  @Test
  void suggestCommand_alsoConsidersAliases() {
    assertThat(registry.suggestCommand("v")).contains("vs");
  }

  @Test
  void suggestSubcommand_offersTheNearestSubcommandOfThatCommand() {
    assertThat(registry.suggestSubcommand("stats", "overal")).contains("overall");
    assertThat(registry.suggestSubcommand("stats", "item")).contains("items");
  }

  @Test
  void suggestSubcommand_unknownCommand_isEmpty() {
    assertThat(registry.suggestSubcommand("nope", "overall")).isEmpty();
  }

  @Test
  void suggestSubcommand_commandWithoutSubcommands_isEmpty() {
    assertThat(registry.suggestSubcommand("scout", "anything")).isEmpty();
  }

  @Test
  void buildHelpEmbed_listsEverySubcommandAndAlias() {
    String rendered = registry.buildHelpEmbed().getDescription();

    assertThat(rendered)
        .contains("/stats overall")
        .contains("/stats items")
        .contains("/scout")
        .contains("!vs");
  }

  /** Minimal DbuffCommand for registry tests — records invocations, does nothing else. */
  private static class StubCommand implements DbuffCommand {
    private final String name;
    private final SlashCommandData definition;
    private final List<String> aliases;

    StubCommand(String name, SlashCommandData definition, List<String> aliases) {
      this.name = name;
      this.definition = definition;
      this.aliases = aliases;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public SlashCommandData getDefinition() {
      return definition;
    }

    @Override
    public List<String> getTextAliases() {
      return aliases;
    }

    @Override
    public void execute(String subcommand, CommandContext context) {
      // no-op
    }
  }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.CommandRegistryTest"`

Expected: **compilation failure** — `cannot find symbol: class CommandRegistry`.

- [ ] **Step 7: Write `CommandRegistry`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/CommandRegistry.java`:

```java
package com.ako.dbuff.service.discord.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for which commands exist.
 *
 * <p>Both adapters dispatch through this, JDA registration reads its definitions, and help text is
 * generated from it — so help cannot drift from the actual command set.
 */
@Component
public class CommandRegistry {

  private static final int MAX_SUGGESTION_DISTANCE = 4;
  private static final int EMBED_COLOR = 0x00AE86;

  private final List<DbuffCommand> commands;
  private final Map<String, DbuffCommand> byName = new LinkedHashMap<>();
  private final Map<String, DbuffCommand> byTextAlias = new LinkedHashMap<>();

  public CommandRegistry(List<DbuffCommand> commands) {
    this.commands = List.copyOf(commands);
    for (DbuffCommand command : commands) {
      byName.put(command.getName().toLowerCase(), command);
      byTextAlias.put(command.getName().toLowerCase(), command);
      for (String alias : command.getTextAliases()) {
        byTextAlias.put(alias.toLowerCase(), command);
      }
    }
  }

  public Optional<DbuffCommand> findByName(String name) {
    return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase()));
  }

  public Optional<DbuffCommand> findByTextAlias(String alias) {
    return alias == null
        ? Optional.empty()
        : Optional.ofNullable(byTextAlias.get(alias.toLowerCase()));
  }

  /** All JDA definitions, for guild registration. */
  public List<SlashCommandData> getDefinitions() {
    return commands.stream().map(DbuffCommand::getDefinition).toList();
  }

  /** Nearest known command name or alias, for "did you mean" on a mistyped text command. */
  public Optional<String> suggestCommand(String input) {
    return TextSimilarity.closest(input, byTextAlias.keySet(), MAX_SUGGESTION_DISTANCE);
  }

  /** Nearest subcommand of {@code commandName}, or empty if it has none or does not exist. */
  public Optional<String> suggestSubcommand(String commandName, String input) {
    return findByName(commandName)
        .map(command -> subcommandNames(command.getDefinition()))
        .filter(names -> !names.isEmpty())
        .flatMap(names -> TextSimilarity.closest(input, names, MAX_SUGGESTION_DISTANCE));
  }

  /** Help embed generated from the registered definitions. */
  public MessageEmbed buildHelpEmbed() {
    StringBuilder description = new StringBuilder();

    for (DbuffCommand command : commands) {
      SlashCommandData definition = command.getDefinition();
      List<String> subcommands = subcommandNames(definition);

      if (subcommands.isEmpty()) {
        description
            .append("`/")
            .append(definition.getName())
            .append("` — ")
            .append(definition.getDescription())
            .append('\n');
      } else {
        description.append("**/").append(definition.getName()).append("**\n");
        for (SubcommandData subcommand : definition.getSubcommands()) {
          description
              .append("`/")
              .append(definition.getName())
              .append(' ')
              .append(subcommand.getName())
              .append("` — ")
              .append(subcommand.getDescription())
              .append('\n');
        }
      }

      if (!command.getTextAliases().isEmpty()) {
        description.append("*also: ");
        description.append(
            String.join(", ", command.getTextAliases().stream().map(a -> "!" + a).toList()));
        description.append("*\n");
      }
      description.append('\n');
    }

    return new EmbedBuilder()
        .setTitle("📖 DBuff Commands")
        .setDescription(description.toString().trim())
        .setColor(EMBED_COLOR)
        .build();
  }

  private List<String> subcommandNames(SlashCommandData definition) {
    List<String> names = new ArrayList<>();
    definition.getSubcommands().forEach(subcommand -> names.add(subcommand.getName()));
    return names;
  }
}
```

> If `SlashCommandData#getSubcommands()` does not exist in JDA 6.3.0, keep a
> `List<String>` of subcommand names on `DbuffCommand` as an extra method instead
> of reading them back off the definition. Check the javadoc before writing this.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.CommandRegistryTest" --tests "com.ako.dbuff.service.discord.command.TextSimilarityTest" --tests "com.ako.dbuff.service.constant.ConstantNameResolverTest"`

Expected: **PASS**, all three classes.

- [ ] **Step 9: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/discord/command/ \
        server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java \
        server/src/test/java/com/ako/dbuff/service/discord/command/
git commit -m "feat: add command registry with generated help and typo suggestions"
```

---

## Task 3: Autocomplete framework and comma accumulation

The novel logic here is list accumulation. Discord options are single-valued, so a
list arrives as one comma-separated string. Autocomplete must complete only the
**last** token while preserving everything before it, and return the whole
accumulated string as the choice value.

Both Discord limits bite here: the choice **value** caps at 100 characters and the
choice **name** caps at 100 characters. When accumulation would exceed either, the
choice must be dropped rather than truncated — a truncated value would submit a
different item than the one displayed.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/AutocompleteProvider.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/ChoiceAccumulator.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/HeroAutocomplete.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/ItemAutocomplete.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/AbilityAutocomplete.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/GameModeAutocomplete.java`
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/autocomplete/ChoiceAccumulatorTest.java`
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/autocomplete/ItemAutocompleteTest.java`

- [ ] **Step 1: Write the failing test for `ChoiceAccumulator`**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/autocomplete/ChoiceAccumulatorTest.java`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceAccumulatorTest {

  /** Display name -> submitted value, in the shape the constant providers supply. */
  private static final Map<String, String> CANDIDATES =
      Map.of(
          "Blink Dagger", "blink",
          "Black King Bar", "black_king_bar",
          "Battle Fury", "battle_fury",
          "Manta Style", "manta");

  @Test
  void emptyInput_offersEverything() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("", CANDIDATES);

    assertThat(choices).hasSize(4);
  }

  @Test
  void singleToken_matchesBySubstringCaseInsensitively() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void matchesDisplayNameNotJustValue() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("King", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Black King Bar");
  }

  @Test
  void secondToken_preservesTheFirstAndCompletesOnlyTheLast() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink, black k", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger, Black King Bar");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void trailingComma_offersEverythingForTheNewToken() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink,", CANDIDATES);

    // All four candidates, each appended to the resolved first token.
    assertThat(choices).hasSize(4);
    assertThat(choices)
        .allSatisfy(choice -> assertThat(choice.getAsString()).startsWith("blink,"));
  }

  @Test
  void alreadyChosenEntriesAreNotOfferedAgain() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("blink, bl", CANDIDATES);

    // "Blink Dagger" is already in the prefix, so only Black King Bar matches "bl".
    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void unresolvablePrefixToken_isPreservedVerbatim() {
    List<Command.Choice> choices = ChoiceAccumulator.accumulate("mystery, blink", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("mystery,blink");
  }

  @Test
  void noMatches_isEmptyNotAnError() {
    assertThat(ChoiceAccumulator.accumulate("zzzzz", CANDIDATES)).isEmpty();
  }

  @Test
  void resultIsCappedAtDiscordsTwentyFiveChoices() {
    Map<String, String> many = new java.util.HashMap<>();
    for (int i = 0; i < 60; i++) {
      many.put("Item Number " + i, "item_" + i);
    }

    assertThat(ChoiceAccumulator.accumulate("Item", many)).hasSize(25);
  }

  @Test
  void choicesExceedingTheValueLengthLimitAreDroppedNotTruncated() {
    // A prefix long enough that appending anything breaks the 100-char value limit.
    String longPrefix = "a".repeat(95);
    Map<String, String> candidates = Map.of("Blink Dagger", "blink");

    List<Command.Choice> choices =
        ChoiceAccumulator.accumulate(longPrefix + ", bli", candidates);

    assertThat(choices).isEmpty();
  }

  @Test
  void nullInput_isTreatedAsEmpty() {
    assertThat(ChoiceAccumulator.accumulate(null, CANDIDATES)).hasSize(4);
  }

  @Test
  void singleValued_doesNotAccumulate() {
    List<Command.Choice> choices = ChoiceAccumulator.single("blink", CANDIDATES);

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void singleValued_ignoresCommas() {
    // A single-valued option like hero: must not build a list.
    List<Command.Choice> choices = ChoiceAccumulator.single("blink, black", CANDIDATES);

    assertThat(choices).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.autocomplete.ChoiceAccumulatorTest"`

Expected: **compilation failure** — `cannot find symbol: class ChoiceAccumulator`.

- [ ] **Step 3: Write `ChoiceAccumulator`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/ChoiceAccumulator.java`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Turns a partially typed option value into Discord autocomplete choices.
 *
 * <p>List-valued options arrive as one comma-separated string because Discord options are
 * single-valued. {@link #accumulate} completes only the final token and rebuilds the whole string,
 * so selecting a suggestion replaces the entire option value with prefix-plus-selection.
 */
public final class ChoiceAccumulator {

  /** Discord returns at most this many autocomplete suggestions. */
  public static final int MAX_CHOICES = 25;

  /** Discord rejects a choice whose submitted value exceeds this length. */
  public static final int MAX_VALUE_LENGTH = 100;

  /** Discord rejects a choice whose display name exceeds this length. */
  public static final int MAX_NAME_LENGTH = 100;

  private ChoiceAccumulator() {}

  /**
   * Choices for a list-valued option.
   *
   * @param currentInput what the user has typed so far, e.g. {@code "blink, black k"}
   * @param candidates display name to submitted value, e.g. {@code "Blink Dagger" -> "blink"}
   */
  public static List<Command.Choice> accumulate(
      String currentInput, Map<String, String> candidates) {

    String input = currentInput == null ? "" : currentInput;
    int lastComma = input.lastIndexOf(',');

    String prefixRaw = lastComma < 0 ? "" : input.substring(0, lastComma);
    String activeToken = lastComma < 0 ? input.trim() : input.substring(lastComma + 1).trim();

    List<String> prefixValues = splitTokens(prefixRaw);
    Set<String> alreadyChosen = new LinkedHashSet<>(resolveAll(prefixValues, candidates));

    String prefixValue = String.join(",", alreadyChosen);
    String prefixDisplay = String.join(", ", displayFor(alreadyChosen, candidates));

    List<Command.Choice> choices = new ArrayList<>();
    for (Map.Entry<String, String> candidate : sortedByDisplayName(candidates)) {
      if (choices.size() >= MAX_CHOICES) {
        break;
      }
      String display = candidate.getKey();
      String value = candidate.getValue();

      if (alreadyChosen.contains(value)) {
        continue;
      }
      if (!matches(activeToken, display, value)) {
        continue;
      }

      String fullValue = prefixValue.isEmpty() ? value : prefixValue + "," + value;
      String fullDisplay = prefixDisplay.isEmpty() ? display : prefixDisplay + ", " + display;

      // Drop rather than truncate: a truncated value would submit a different
      // item than the one shown.
      if (fullValue.length() > MAX_VALUE_LENGTH || fullDisplay.length() > MAX_NAME_LENGTH) {
        continue;
      }
      choices.add(new Command.Choice(fullDisplay, fullValue));
    }
    return choices;
  }

  /**
   * Choices for a single-valued option such as {@code hero:}. Never accumulates — an input
   * containing a comma matches nothing, which is the correct signal that a list is not accepted here.
   */
  public static List<Command.Choice> single(String currentInput, Map<String, String> candidates) {
    String input = currentInput == null ? "" : currentInput.trim();
    if (input.contains(",")) {
      return List.of();
    }

    List<Command.Choice> choices = new ArrayList<>();
    for (Map.Entry<String, String> candidate : sortedByDisplayName(candidates)) {
      if (choices.size() >= MAX_CHOICES) {
        break;
      }
      if (!matches(input, candidate.getKey(), candidate.getValue())) {
        continue;
      }
      if (candidate.getValue().length() > MAX_VALUE_LENGTH
          || candidate.getKey().length() > MAX_NAME_LENGTH) {
        continue;
      }
      choices.add(new Command.Choice(candidate.getKey(), candidate.getValue()));
    }
    return choices;
  }

  private static boolean matches(String token, String display, String value) {
    if (token.isEmpty()) {
      return true;
    }
    String needle = token.toLowerCase();
    return display.toLowerCase().contains(needle) || value.toLowerCase().contains(needle);
  }

  private static List<String> splitTokens(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  /**
   * Maps each prefix token to its canonical value, keeping the token verbatim when it resolves to
   * nothing — the user may be mid-edit, and silently deleting their text would be hostile.
   */
  private static List<String> resolveAll(List<String> tokens, Map<String, String> candidates) {
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      resolved.add(canonicalValue(token, candidates).orElse(token));
    }
    return resolved;
  }

  private static java.util.Optional<String> canonicalValue(
      String token, Map<String, String> candidates) {
    return candidates.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().equalsIgnoreCase(token) || entry.getValue().equalsIgnoreCase(token))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  private static List<String> displayFor(Set<String> values, Map<String, String> candidates) {
    List<String> displays = new ArrayList<>();
    for (String value : values) {
      displays.add(
          candidates.entrySet().stream()
              .filter(entry -> entry.getValue().equals(value))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse(value));
    }
    return displays;
  }

  private static List<Map.Entry<String, String>> sortedByDisplayName(
      Map<String, String> candidates) {
    return candidates.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.autocomplete.ChoiceAccumulatorTest"`

Expected: **PASS**, 13 tests.

- [ ] **Step 5: Write `AutocompleteProvider`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/AutocompleteProvider.java`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Supplies autocomplete choices for one option name.
 *
 * <p>Implementations must never throw and must return within Discord's three-second autocomplete
 * budget. A provider that throws leaves the user with a silently broken picker and no explanation,
 * so the adapter catches everything — but providers should not rely on that.
 */
public interface AutocompleteProvider {

  /** The option name this provider serves, e.g. {@code items}. */
  String getOptionName();

  /** The command this provider belongs to, e.g. {@code stats}. */
  String getCommandName();

  /**
   * @param currentInput what the user has typed into this option so far
   * @param context the in-flight command context, for providers that need channel scope
   */
  List<Command.Choice> getChoices(String currentInput, CommandContext context);
}
```

- [ ] **Step 6: Write the failing test for `ItemAutocomplete`**

Create `server/src/test/java/com/ako/dbuff/service/discord/command/autocomplete/ItemAutocompleteTest.java`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ItemAutocompleteTest {

  private ConstantsManagers constantsManagers;
  private ItemAutocomplete provider;

  @BeforeEach
  void setUp() {
    constantsManagers = Mockito.mock(ConstantsManagers.class);
    Mockito.when(constantsManagers.getItemConstantMap())
        .thenReturn(
            Map.of(
                "blink", ItemConstant.builder().id(1L).dname("Blink Dagger").build(),
                "black_king_bar", ItemConstant.builder().id(2L).dname("Black King Bar").build()));
    provider = new ItemAutocomplete(constantsManagers);
  }

  @Test
  void servesTheItemsOptionOfTheStatsCommand() {
    assertThat(provider.getOptionName()).isEqualTo("items");
    assertThat(provider.getCommandName()).isEqualTo("stats");
  }

  @Test
  void offersDisplayNamesAndSubmitsShortNames() {
    java.util.List<Command.Choice> choices =
        provider.getChoices("blink", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void accumulatesAcrossCommas() {
    java.util.List<Command.Choice> choices =
        provider.getChoices("blink, black", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void itemsWithoutADisplayNameAreSkipped() {
    Mockito.when(constantsManagers.getItemConstantMap())
        .thenReturn(Map.of("weird", ItemConstant.builder().id(9L).dname(null).build()));

    assertThat(provider.getChoices("weird", FakeCommandContext.builder().build())).isEmpty();
  }

  @Test
  void constantLookupFailure_returnsEmptyRatherThanThrowing() {
    Mockito.when(constantsManagers.getItemConstantMap())
        .thenThrow(new IllegalStateException("cache cold"));

    assertThat(provider.getChoices("blink", FakeCommandContext.builder().build())).isEmpty();
  }
}
```

- [ ] **Step 7: Write the four constant-backed providers**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/ItemAutocomplete.java`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/** Item autocomplete over the in-memory item constant cache. List-valued, so it accumulates. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "items";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Item autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (Map.Entry<String, ItemConstant> entry : constantsManagers.getItemConstantMap().entrySet()) {
      String display = entry.getValue().getDname();
      if (display != null && !display.isBlank()) {
        candidates.put(display, entry.getKey());
      }
    }
    return candidates;
  }
}
```

Create `HeroAutocomplete.java` in the same package. It is **single-valued** — the
spec fixes `hero:` at one value — so it calls `ChoiceAccumulator.single`:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/** Hero autocomplete. Single-valued: a game has exactly one hero, so lists are not accepted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeroAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "hero";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Hero autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (HeroConstant hero : constantsManagers.getHeroConstantMap().values()) {
      String display = hero.getLocalized_name();
      if (display != null && !display.isBlank()) {
        // Submit the localized name — ConstantNameResolver accepts it, and it round-trips
        // legibly if the user edits the value by hand.
        candidates.put(display, display);
      }
    }
    return candidates;
  }
}
```

Create `AbilityAutocomplete.java`. List-valued, and it **narrows to the chosen
hero's abilities** when `hero:` is already set:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Ability autocomplete. List-valued, and narrowed to the selected hero's abilities when the {@code
 * hero:} option is already filled in — otherwise the picker offers well over a thousand entries and
 * the 25-choice cap makes it useless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "skills";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, displayToValue(context.getOption("hero")));
    } catch (Exception e) {
      log.debug("Ability autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue(String heroName) {
    Set<String> heroAbilities = abilitiesOfHero(heroName);

    Map<String, String> candidates = new LinkedHashMap<>();
    for (AbilityIdsConstant ability : constantsManagers.getAbilityConstantMap().values()) {
      String name = ability.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      if (heroAbilities != null && !heroAbilities.contains(name)) {
        continue;
      }
      candidates.put(name, name);
    }
    return candidates;
  }

  /**
   * The internal ability names belonging to {@code heroName}, or null when no hero was chosen (so no
   * narrowing is applied).
   *
   * <p>Consult {@code HeroAbilitiesConstantService} for the hero-to-abilities mapping. Read that
   * class before implementing: confirm whether it is keyed by internal hero name
   * ({@code npc_dota_hero_invoker}) or localized name, and convert accordingly.
   */
  private Set<String> abilitiesOfHero(String heroName) {
    if (heroName == null || heroName.isBlank()) {
      return null;
    }
    return constantsManagers.getAllHeroAbilities().isEmpty() ? null : resolveHeroAbilities(heroName);
  }

  private Set<String> resolveHeroAbilities(String heroName) {
    // Implement against HeroAbilitiesConstantService's actual map shape.
    // Return null if the hero cannot be matched, so the picker degrades to
    // offering all abilities rather than offering none.
    return null;
  }
}
```

> **`resolveHeroAbilities` is the one stub in this plan.** It cannot be written
> without reading `HeroAbilitiesConstantService` and
> `ConstantsManagers#getAllHeroAbilities()` to establish the map's key and value
> shapes. Read both, then implement it, and add a test asserting that a hero with
> known abilities narrows the candidate set. Returning null (no narrowing) is the
> correct failure mode — never return an empty set, which would make the picker
> appear broken.

Create `GameModeAutocomplete.java`, serving `/dbuff`'s `mode:` option. Single-valued
per the spec. It offers mode **names** from `getMatchTypeConstantMap()` while the
domain stores numeric IDs, so it submits the numeric ID as the value:

```java
package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/** Game mode autocomplete: shows human-readable mode names, submits the numeric mode ID. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameModeAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "mode";
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Game mode autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (Map.Entry<String, MatchTypeConstant> entry :
        constantsManagers.getMatchTypeConstantMap().entrySet()) {
      // Read MatchTypeConstant before writing this: confirm which field holds the
      // display name and which the numeric ID, and whether the map key is the ID.
      candidates.put(displayNameOf(entry), entry.getKey());
    }
    return candidates;
  }

  private String displayNameOf(Map.Entry<String, MatchTypeConstant> entry) {
    MatchTypeConstant mode = entry.getValue();
    return mode.getName() != null ? mode.getName() : entry.getKey();
  }
}
```

> Verify `MatchTypeConstant`'s field names before writing `displayNameOf`. If it has
> no `getName()`, use whatever field holds the human-readable label.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.autocomplete.*"`

Expected: **PASS**.

- [ ] **Step 9: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/discord/command/autocomplete/ \
        server/src/test/java/com/ako/dbuff/service/discord/command/autocomplete/
git commit -m "feat: add autocomplete providers with comma-list accumulation"
```

---

## Task 4: The two adapters

The trickiest task in the plan. Everything Discord-specific lives here so that no
handler has to know about interaction tokens, thread creation, or the 3-second
window.

Four behaviors that must be right:

1. **Acknowledge, then thread, then work.** `event.reply(summary)` →
   `event.getHook().retrieveOriginal()` → `message.createThreadChannel(name)` →
   hand the thread to the handler on a virtual thread.
2. **Never create a thread inside a thread.** Discord forbids nesting, so when
   invoked in a thread the reply goes into the current thread.
3. **Ephemeral before ack only.** Discord will not attach a thread to an ephemeral
   message, so `replyEphemeral` must be terminal.
4. **The gateway thread is never blocked.** All post-ack work runs on a virtual
   thread, as `PlayerScoutDiscordListener` already does today.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/ThreadAsyncReply.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/InteractionCommandContext.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/SlashCommandAdapter.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/MessageCommandContext.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/TextCommandAdapter.java`
- Create: `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/TextCommandParser.java`
- Test: `server/src/test/java/com/ako/dbuff/service/discord/command/adapter/TextCommandParserTest.java`

- [ ] **Step 1: Write the failing test for the text parser**

The parser is the only pure-logic piece of the adapters, so it gets real tests.
The JDA-bound classes are verified by hand in step 8.

Create `server/src/test/java/com/ako/dbuff/service/discord/command/adapter/TextCommandParserTest.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCommandParserTest {

  @Test
  void parsesDbuffSubcommandWithArguments() {
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

    assertThat(parsed).isPresent();
    assertThat(parsed.get().subcommand()).isEqualTo("add-players");
    assertThat(parsed.get().arguments()).isEqualTo("111 222");
  }

  @Test
  void parsesAliasWithNoSubcommand() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!vs Termit");

    assertThat(parsed).isPresent();
    assertThat(parsed.get().alias()).isEqualTo("vs");
    assertThat(parsed.get().subcommand()).isNull();
    assertThat(parsed.get().arguments()).isEqualTo("Termit");
  }

  @Test
  void parsesBareAlias() {
    Optional<TextCommandParser.ParsedCommand> parsed = TextCommandParser.parse("!rerun");

    assertThat(parsed).isPresent();
    assertThat(parsed.get().alias()).isEqualTo("rerun");
    assertThat(parsed.get().subcommand()).isNull();
    assertThat(parsed.get().arguments()).isEmpty();
  }

  @Test
  void isCaseInsensitiveOnTheAlias() {
    assertThat(TextCommandParser.parse("!VS Termit").get().alias()).isEqualTo("vs");
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.adapter.TextCommandParserTest"`

Expected: **compilation failure** — `cannot find symbol: class TextCommandParser`.

- [ ] **Step 3: Write `TextCommandParser`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/TextCommandParser.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import java.util.Optional;

/**
 * Splits a legacy {@code !} text command into alias, subcommand, and remaining arguments.
 *
 * <p>Whether the second token is a subcommand or an argument depends on the command, so both are
 * exposed and {@link TextCommandAdapter} decides using the registry.
 */
public final class TextCommandParser {

  private static final String PREFIX = "!";

  private TextCommandParser() {}

  /**
   * @param alias the command alias, lower-cased, without the {@code !}
   * @param subcommand the second token, or null when there was only one token
   * @param arguments everything after the subcommand, never null
   */
  public record ParsedCommand(String alias, String subcommand, String arguments) {}

  public static Optional<ParsedCommand> parse(String rawContent) {
    if (rawContent == null) {
      return Optional.empty();
    }
    String content = rawContent.trim();
    if (!content.startsWith(PREFIX)) {
      return Optional.empty();
    }

    String body = content.substring(PREFIX.length()).trim();
    if (body.isEmpty()) {
      return Optional.empty();
    }

    String[] parts = body.split("\\s+", 3);
    String alias = parts[0].toLowerCase();
    String subcommand = parts.length > 1 ? parts[1] : null;
    String arguments = parts.length > 2 ? parts[2].trim() : "";

    return Optional.of(new ParsedCommand(alias, subcommand, arguments));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.discord.command.adapter.TextCommandParserTest"`

Expected: **PASS**, 9 tests.

- [ ] **Step 5: Write `ThreadAsyncReply`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/ThreadAsyncReply.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Posts results into an already-resolved channel — a freshly created thread, or the thread the
 * command was invoked in.
 *
 * <p>Uses {@code complete()} rather than {@code queue()} so that ordering is preserved: results are
 * posted one after another and a caller that loops sees them appear in order. Safe because this only
 * ever runs on a virtual thread, never on the JDA gateway thread.
 */
@Slf4j
@RequiredArgsConstructor
public class ThreadAsyncReply implements AsyncReply {

  /** Discord rejects message content longer than this. */
  private static final int MAX_MESSAGE_LENGTH = 2000;

  private final MessageChannel channel;

  @Override
  public void post(String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    for (String chunk : splitToLimit(message)) {
      channel.sendMessage(chunk).complete();
    }
  }

  @Override
  public void postEmbed(MessageEmbed embed) {
    channel.sendMessageEmbeds(embed).complete();
  }

  @Override
  public void fail(String message) {
    log.warn("Command failed in channel {}: {}", channel.getId(), message);
    channel.sendMessage("❌ " + truncate(message)).complete();
  }

  /** Splits on newlines where possible, hard-splitting only when a single line is too long. */
  private static java.util.List<String> splitToLimit(String message) {
    if (message.length() <= MAX_MESSAGE_LENGTH) {
      return java.util.List.of(message);
    }
    java.util.List<String> chunks = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (String line : message.split("\n", -1)) {
      while (line.length() > MAX_MESSAGE_LENGTH) {
        if (current.length() > 0) {
          chunks.add(current.toString());
          current.setLength(0);
        }
        chunks.add(line.substring(0, MAX_MESSAGE_LENGTH));
        line = line.substring(MAX_MESSAGE_LENGTH);
      }
      if (current.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
        chunks.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) {
        current.append('\n');
      }
      current.append(line);
    }
    if (current.length() > 0) {
      chunks.add(current.toString());
    }
    return chunks;
  }

  private static String truncate(String message) {
    String safe = message == null ? "Unknown error" : message;
    return safe.length() <= MAX_MESSAGE_LENGTH - 2
        ? safe
        : safe.substring(0, MAX_MESSAGE_LENGTH - 5) + "…";
  }
}
```

- [ ] **Step 6: Write `InteractionCommandContext`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/InteractionCommandContext.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** {@link CommandContext} over a slash-command interaction. */
@RequiredArgsConstructor
public class InteractionCommandContext implements CommandContext {

  /** Discord truncates thread names beyond this. */
  private static final int MAX_THREAD_NAME_LENGTH = 100;

  private final SlashCommandInteractionEvent event;

  @Override
  public String getOption(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? null : option.getAsString();
  }

  @Override
  public List<String> getOptionAsList(String name) {
    String raw = getOption(name);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public int getOptionAsInt(String name, int defaultValue) {
    OptionMapping option = event.getOption(name);
    if (option == null) {
      return defaultValue;
    }
    try {
      return option.getAsInt();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  @Override
  public Optional<String> getOptionAsUserId(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? Optional.empty() : Optional.of(option.getAsUser().getId());
  }

  @Override
  public String getInvokerId() {
    return event.getUser().getId();
  }

  @Override
  public String getChannelId() {
    return event.getChannel().getId();
  }

  @Override
  public String getParentChannelId() {
    return event.getChannel() instanceof ThreadChannel thread
        ? thread.getParentChannel().getId()
        : event.getChannel().getId();
  }

  @Override
  public Optional<String> getGuildId() {
    return event.getGuild() == null ? Optional.empty() : Optional.of(event.getGuild().getId());
  }

  @Override
  public boolean isInsideThread() {
    return event.getChannel() instanceof ThreadChannel;
  }

  @Override
  public Optional<String> getThreadName() {
    return event.getChannel() instanceof ThreadChannel thread
        ? Optional.of(thread.getName())
        : Optional.empty();
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    // Reply first: this is what satisfies Discord's 3-second window. Everything
    // after it can take as long as it needs.
    event.reply(summary).complete();

    if (isInsideThread()) {
      // Threads cannot nest, so results go into the current thread.
      return new ThreadAsyncReply(event.getChannel());
    }

    Message original = event.getHook().retrieveOriginal().complete();
    ThreadChannel thread =
        original
            .createThreadChannel(sanitizeThreadName(threadName))
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
            .complete();

    return new ThreadAsyncReply((MessageChannel) thread);
  }

  @Override
  public void replyEphemeral(String message) {
    event.reply(message).setEphemeral(true).complete();
  }

  private static String sanitizeThreadName(String rawName) {
    String name = rawName == null ? "DBuff" : rawName.replaceAll("\\s+", " ").trim();
    if (name.isEmpty()) {
      name = "DBuff";
    }
    return name.length() > MAX_THREAD_NAME_LENGTH
        ? name.substring(0, MAX_THREAD_NAME_LENGTH)
        : name;
  }
}
```

- [ ] **Step 7: Write `SlashCommandAdapter`**

Create `server/src/main/java/com/ako/dbuff/service/discord/command/adapter/SlashCommandAdapter.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.autocomplete.AutocompleteProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Routes slash-command interactions and autocomplete requests to {@link DbuffCommand} handlers, and
 * registers the command definitions with every guild the bot is in on startup.
 *
 * <p>Guild-scoped registration rather than global: it propagates immediately instead of taking up to
 * an hour, which matters when iterating.
 */
@Slf4j
@Component
public class SlashCommandAdapter extends ListenerAdapter {

  private final CommandRegistry registry;
  private final Map<String, AutocompleteProvider> providersByKey;

  public SlashCommandAdapter(CommandRegistry registry, List<AutocompleteProvider> providers) {
    this.registry = registry;
    this.providersByKey =
        providers.stream()
            .collect(
                Collectors.toMap(
                    provider -> providerKey(provider.getCommandName(), provider.getOptionName()),
                    Function.identity(),
                    (first, second) -> first));
  }

  @Override
  public void onReady(ReadyEvent event) {
    for (Guild guild : event.getJDA().getGuilds()) {
      guild
          .updateCommands()
          .addCommands(registry.getDefinitions())
          .queue(
              success ->
                  log.info(
                      "Registered {} slash commands with guild {}",
                      registry.getDefinitions().size(),
                      guild.getId()),
              error ->
                  log.error(
                      "Failed to register slash commands with guild {}: {}",
                      guild.getId(),
                      error.getMessage(),
                      error));
    }
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    Optional<DbuffCommand> command = registry.findByName(event.getName());
    if (command.isEmpty()) {
      // Registered with Discord but no handler bean — a wiring bug, not user error.
      log.error("No handler for slash command /{}", event.getName());
      event.reply("This command is not available right now.").setEphemeral(true).queue();
      return;
    }

    CommandContext context = new InteractionCommandContext(event);
    String subcommand = event.getSubcommandName();

    Thread.startVirtualThread(
        () -> {
          try {
            command.get().execute(subcommand, context);
          } catch (Exception e) {
            log.error(
                "Slash command /{} {} failed: {}",
                event.getName(),
                subcommand,
                e.getMessage(),
                e);
            reportFailure(event, e);
          }
        });
  }

  @Override
  public void onCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
    String key = providerKey(event.getName(), event.getFocusedOption().getName());
    AutocompleteProvider provider = providersByKey.get(key);
    if (provider == null) {
      event.replyChoices(List.of()).queue();
      return;
    }

    List<Command.Choice> choices;
    try {
      choices =
          provider.getChoices(
              event.getFocusedOption().getValue(), new AutocompleteCommandContext(event));
    } catch (Exception e) {
      // A broken picker with no explanation is worse than an empty one.
      log.debug("Autocomplete provider {} failed: {}", key, e.getMessage());
      choices = List.of();
    }
    event.replyChoices(choices).queue();
  }

  /**
   * Best-effort error reporting. The interaction may already be acknowledged, in which case the hook
   * is the only way to reach the user; if it is not, a fresh ephemeral reply works.
   */
  private void reportFailure(SlashCommandInteractionEvent event, Exception cause) {
    String message = "❌ " + (cause.getMessage() == null ? "Unexpected error" : cause.getMessage());
    try {
      if (event.isAcknowledged()) {
        event.getHook().sendMessage(message).setEphemeral(true).queue();
      } else {
        event.reply(message).setEphemeral(true).queue();
      }
    } catch (Exception e) {
      log.error("Could not report failure to the user: {}", e.getMessage());
    }
  }

  private static String providerKey(String commandName, String optionName) {
    return commandName.toLowerCase() + ":" + optionName.toLowerCase();
  }
}
```

You also need a minimal `CommandContext` for autocomplete, because
`AbilityAutocomplete` reads the sibling `hero:` option. Create
`server/src/main/java/com/ako/dbuff/service/discord/command/adapter/AutocompleteCommandContext.java`:

```java
package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Read-only {@link CommandContext} for autocomplete, where sibling option values are available but
 * replying is not. The reply methods throw rather than silently doing nothing, because calling them
 * from a provider is a programming error.
 */
@RequiredArgsConstructor
public class AutocompleteCommandContext implements CommandContext {

  private final CommandAutoCompleteInteractionEvent event;

  @Override
  public String getOption(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? null : option.getAsString();
  }

  @Override
  public List<String> getOptionAsList(String name) {
    String raw = getOption(name);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public int getOptionAsInt(String name, int defaultValue) {
    OptionMapping option = event.getOption(name);
    if (option == null) {
      return defaultValue;
    }
    try {
      return option.getAsInt();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  @Override
  public Optional<String> getOptionAsUserId(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? Optional.empty() : Optional.of(option.getAsUser().getId());
  }

  @Override
  public String getInvokerId() {
    return event.getUser().getId();
  }

  @Override
  public String getChannelId() {
    return event.getChannel().getId();
  }

  @Override
  public String getParentChannelId() {
    return event.getChannel().getId();
  }

  @Override
  public Optional<String> getGuildId() {
    return event.getGuild() == null ? Optional.empty() : Optional.of(event.getGuild().getId());
  }

  @Override
  public boolean isInsideThread() {
    return false;
  }

  @Override
  public Optional<String> getThreadName() {
    return Optional.empty();
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    throw new UnsupportedOperationException("Cannot acknowledge an autocomplete interaction");
  }

  @Override
  public void replyEphemeral(String message) {
    throw new UnsupportedOperationException("Cannot reply to an autocomplete interaction");
  }
}
```

- [ ] **Step 8: Write `MessageCommandContext` and `TextCommandAdapter`**

`MessageCommandContext` mirrors `InteractionCommandContext` with three differences:

- Options come from a `Map<String, String>` that `TextCommandAdapter` builds by
  parsing the argument string, not from JDA option mappings.
- `acknowledge` sends a normal channel message, then creates a thread **on the
  user's original message** — matching what `PlayerScoutDiscordListener` does today.
- `replyEphemeral` has no ephemeral equivalent for text commands, so it sends a
  normal channel message prefixed with the invoker's mention.

Create it accordingly, reusing `ThreadAsyncReply` and the same
`sanitizeThreadName` logic.

`TextCommandAdapter` extends `ListenerAdapter`, and in `onMessageReceived`:

1. Return immediately if `event.getAuthor().isBot()`.
2. `TextCommandParser.parse(event.getMessage().getContentRaw())`; return if empty.
3. `registry.findByTextAlias(parsed.alias())`. If empty, return **silently** — the
   channel may contain unrelated `!` messages from other bots, so complaining about
   every one would be noise.
4. If the alias resolves but the subcommand does not exist for that command, reply
   with `registry.suggestSubcommand(...)`: *"Unknown subcommand `add-player`. Did you
   mean `add-players`?"* This replaces the current silent fall-through to help.
5. Build a `MessageCommandContext` with the parsed arguments mapped to option names.
   The mapping is per-command; put a `Map<String, String> parseTextArguments(String
   subcommand, String arguments)` default method on `DbuffCommand` returning an empty
   map, and override it in the commands that have text aliases (`/scout`, `/match`,
   `/dbuff`). `/stats` has no text alias, so it needs no override.
6. Dispatch on a virtual thread with the same try/catch as the slash adapter.

- [ ] **Step 9: Verify by hand against a real guild**

The JDA-bound classes cannot be unit-tested meaningfully. Verify them live once
the first command exists (Task 6), then return here and confirm all four
behaviors from the top of this task:

```
/stats overall player:<someone>     → public ack, thread created, results in thread
(same, invoked inside a thread)     → results in the current thread, no new thread
/stats overall player:nonexistent   → ephemeral error, no thread created
```

- [ ] **Step 10: Format and commit**

```bash
./gradlew spotlessApply
./gradlew test
git add server/src/main/java/com/ako/dbuff/service/discord/command/adapter/ \
        server/src/test/java/com/ako/dbuff/service/discord/command/adapter/
git commit -m "feat: add slash and text command adapters with thread-backed async replies"
```

---

## Tasks 5–13

The remaining tasks follow the patterns established above. Each is specified
below with its files, option definitions, behavior, and required test assertions.
Write the tests first in every case, and follow the same
red → green → format → commit rhythm.

### Task 5: Player autocomplete providers

**Files:** `autocomplete/TrackedPlayerAutocomplete.java`,
`autocomplete/OpponentAutocomplete.java`,
`autocomplete/OpenDotaPlayerAutocomplete.java`, plus a test per provider.

- `TrackedPlayerAutocomplete` (`stats:player`, `dbuff:player`) — reads the
  channel's instance via `DbufInstanceConfigService.getByDiscordChannelId`, offers
  `PlayerInfo` entries. Display `Termit — @anton` when `discordUserId` is set, else
  just `Termit`; **submits the player name**. List-valued, so
  `ChoiceAccumulator.accumulate`.
- `OpponentAutocomplete` (`scout:name`) — reads known opponents from
  `ExternalPlayerStatisticRepository`. Single-valued.
- `OpenDotaPlayerAutocomplete` (`dbuff:player` on `players add`) — calls OpenDota
  `SearchApi`. Needs **its own** Caffeine cache keyed on the query prefix **and its
  own `RateLimiter`**, separate from the 60/min match-fetch limiter in
  `ConcurrencyConfig`. Autocomplete fires per keystroke; sharing the limiter would
  let a user typing in a search box starve match ingestion. Submits the numeric
  account ID as the value and shows `Name (12345678)` as the display.

**Required assertions:** the 25-choice cap holds; a cache hit makes no second API
call; a rate-limited or failing API call returns an empty list rather than throwing;
tracked-player display includes the `@nick` only when linked.

### Task 6: `/stats overall`

**Files:** `impl/StatsCommand.java`, `StatsEmbedFormatter.java`, tests for both.

Definition: `Commands.slash("stats", "Player statistics")` with subcommand
`overall`, options `player` (STRING, required, autocomplete), `hero` (STRING,
optional, autocomplete), `period` (STRING, optional, four static choices from
`StatsPeriod.getChoiceValue()`).

Behavior, in this order — the order is the contract:

1. Resolve the channel's instance; if none, `replyEphemeral` pointing at
   `/dbuff register`. **Return.**
2. Resolve `player` entries to account IDs. An `@mention` resolves via
   `PlayerRepo.findByDiscordUserId`; a plain name via the instance's focus group.
   Any unresolved entry → `replyEphemeral` naming it, with a `TextSimilarity`
   suggestion from the focus group. **Return.**
3. More than **5** players → `replyEphemeral`. **Return.**
4. Resolve `hero` via `ConstantNameResolver`; unresolved → `replyEphemeral` with
   `suggestHero`. **Return.**
5. `acknowledge("Fetching stats for N player(s)…", "stats: <names>")`.
6. Per player, **posting each embed as it completes**: call
   `PlayerStatisticService.getPlayerStatistics`, format, `postEmbed`. A failure for
   one player posts an error for that player and continues to the next.
7. If `StatsPeriod.Range.fellBack()` is true, post a note saying the patch date was
   unavailable and 30 days was used instead.

`StatsEmbedFormatter.formatOverall` omits the popular-heroes field when
`response.getHeroFiltered()` is true, and renders a null `avgUseCount` or null
metric as `—`.

**Required assertions** (all against `FakeCommandContext`, no JDA):
no instance → one ephemeral reply and zero posts; unknown player → ephemeral names
that player; 6 players → ephemeral, nothing acknowledged; happy path with 2 players
→ acknowledged once, two embeds; one player throwing → one embed plus one failure,
not an aborted command; `heroFiltered` true → no popular-heroes field; period
fallback → an extra note posted.

### Task 7: `/stats heroes`

Adds subcommand `heroes` to the same `StatsCommand`, with options `player`,
`period`, `limit` (INTEGER, optional, default 10, **max 25**). **No `hero:`
option** — per the spec, its filtered case is served better by
`/stats overall hero:`.

Renders `PlayerStatisticResponse.popularHeroes` as a compact table: hero, games,
win %, avg KDA. Pass `limit` as the `heroLimit` argument.

**Required assertions:** `limit` above 25 is clamped to 25, not rejected; `limit`
below 1 falls back to 10; no games in range → a "no matches" message rather than an
empty embed.

### Task 8: `/stats items`

Adds subcommand `items`: options `player`, `items` (STRING, optional,
autocomplete, list), `hero`, `period`, `limit`.

The two-mode branch is the substance:

- `items` **absent** → `ItemRankingService.getItemRankings(...)`, rendered as a
  top-N table: item, picks, pick %, win %, avg purchase time, avg uses.
- `items` **present** → `ItemRankingService.getItemComboStatistics(...)`, rendered
  as: games found, win %, avg KDA, then one line per member with avg purchase time
  and avg uses.

Catch `UnknownConstantNameException` around resolution and convert it to an
ephemeral reply listing each unknown name with its `suggestItem` suggestion.
Because this happens **before** `acknowledge`, no thread is created for a typo.

**Required assertions:** absent `items` calls the ranking service and not the combo
service, and vice versa; `UnknownConstantNameException` produces an ephemeral reply
with the suggestion and **zero** calls to either service; combo mode with zero games
found posts an explicit "no games with that combination" message; a null
`avgUseCount` renders as `—` and never as `0.00`.

### Task 9: `/stats skills`

Identical in shape to Task 8, substituting:

| Task 8 | Task 9 |
|---|---|
| `items` option | `skills` option |
| `ItemRankingService.getItemRankings` | `AbilityRankingService.getAbilityRankings` |
| `getItemComboStatistics` | `getAbilityComboStatistics` |
| `ItemRankingResponse` | `AbilityRankingResponse` |
| `ItemComboStatisticResponse` | `AbilityComboStatisticResponse` |
| `suggestItem` | `suggestAbility` |
| avg purchase time column | *(omitted — abilities have none)* |

Same assertion list, minus purchase time.

### Task 10: `/scout` and the image button

**Files:** `impl/ScoutCommand.java`, `ScoreboardButtonListener.java`, tests.

`/scout player name:<autocomplete>` and `/scout scoreboard image:<ATTACHMENT>`.
Text alias: `vs`, mapping its argument string to the `name` option.

Move the logic from `PlayerScoutDiscordListener` — it already creates threads and
runs on virtual threads, so it maps onto `AsyncReply` almost directly. Keep
`DiscordStatisticFormatter` as-is; it has tests.

`ScoreboardButtonListener` replaces the silent auto-OCR. On a message with an image
attachment in a registered channel, attach a `Button.primary("scout-scoreboard:" +
messageId, "🔍 Scout this scoreboard")`. On click, run the existing
`ScoreboardStatisticService` path in a thread. This is the behavior change that
stops memes and clips from costing OpenAI Vision calls.

**Required assertions:** a non-image attachment gets no button; an image in an
**unregistered** channel gets no button; clicking runs the scout exactly once;
`!vs Termit` and `/scout player name:Termit` reach the same handler with the same
resolved option.

### Task 11: `/match`

**Files:** `impl/MatchCommand.java`, tests.

`/match rerun` and `/match retry`. Text aliases `rerun` and `retry` — note these
are aliases of *subcommands*, so `getTextAliases` returns both and
`parseTextArguments` maps the alias itself to the subcommand.

Thread-only per the spec. Outside a thread, `replyEphemeral` explaining that. The
match ID comes from `context.getThreadName()`, parsed as in
`PingPongListener.parseMatchIdFromThread`. Move that logic across and keep the
exact-prefix behavior — no schema change, no `match_id` option.

**Required assertions:** invoked outside a thread → ephemeral, no work; thread name
with no parseable ID → ephemeral; valid thread → the orchestrator is called once
with the right match; `!rerun` and `/match rerun` behave identically.

### Task 12: `/dbuff`

**Files:** `impl/DbuffInstanceCommand.java`, `impl/DbuffPlayersCommand.java`, tests.

Split in two because nine subcommands in one class stops being readable.

- `DbuffInstanceCommand` — `register` (single `player:`, optional `name:`,
  `mode:`), `status`, `deactivate` (with a confirmation button), `help` (renders
  `registry.buildHelpEmbed()`).
- `DbuffPlayersCommand` — `players add` (`player:` + optional `user:`),
  `players remove`, `link` (`player:` + `user:`), `modes add`, `modes remove`.

Text alias `dbuf` for both, with `parseTextArguments` mapping the legacy positional
and `--flag` forms onto the new option names so that
`!dbuf register 111 --modes 22 --name Squad` still works.

`register` takes **one** player, per the spec; `/dbuff players add` covers the rest.

**Required assertions:** every legacy `!dbuf` form from
`RegistrationDiscordListener`'s Javadoc still reaches the right handler with
equivalent options — write one test per legacy form, they are the regression suite
for the migration; `deactivate` does nothing until the button is clicked; `link`
sets `discordUserId`; `modes add` accepts a mode **name** and stores the numeric ID.

### Task 13: Wire it up and delete the old listeners

**Files:** modify `BotConfiguration.java`; delete `RegistrationDiscordListener.java`,
`PlayerScoutDiscordListener.java`, `PingPongListener.java`,
`MatchSummaryDiscordListener.java`.

`BotConfiguration` registers only `SlashCommandAdapter`, `TextCommandAdapter`, and
`ScoreboardButtonListener`, keeping `GatewayIntent.MESSAGE_CONTENT` — the text
aliases and the image-button trigger both require it.

Delete `MatchSummaryDiscordListener` outright: it was never registered, and its
`!ping` only answered bots.

- [ ] Confirm `./gradlew test` passes.
- [ ] Confirm `grep -rn "PingPongListener\|RegistrationDiscordListener\|PlayerScoutDiscordListener\|MatchSummaryDiscordListener" server/src/` returns nothing.
- [ ] Start the bot against a real guild and walk every command in the spec's tables, plus every legacy `!` form.
- [ ] Commit.

---

## Plan Self-Review

**Spec coverage.** Every command in the spec's four tables maps to a task:
`/scout` → 10, `/stats overall|heroes|items|skills` → 6, 7, 8, 9, `/match` → 11,
`/dbuff` → 12. Cross-cutting requirements: guild-scoped registration → Task 4
`onReady`; sync-ack-then-thread → Task 4; no thread nesting → Task 4; ephemeral
before ack → Task 4 and enforced by ordering in every handler; comma-list
accumulation with the 100-char and 25-choice caps → Task 3; separate rate limiter
for OpenDota autocomplete → Task 5; `players:` cap of 5 → Task 6; `limit` clamped
to 25 → Task 7; two-mode absent/present branch → 7, 8, 9; `heroFiltered` honored →
Task 6; generated help → Task 2; typo suggestions → Task 2 and Task 12; button
instead of auto-OCR → Task 10; old listeners deleted → Task 13.

**Placeholder scan.** One acknowledged stub: `resolveHeroAbilities` in
`AbilityAutocomplete` (Task 3), which cannot be written without reading
`HeroAbilitiesConstantService`'s map shape. It is flagged inline with its required
failure mode (return null, never an empty set) and a required test. Two smaller
"read the class first" notes, on `MatchTypeConstant`'s field names (Task 3) and
`SlashCommandData#getSubcommands()` (Task 2). These are verification steps, not
deferred decisions.

**Detail asymmetry, stated plainly.** Tasks 1–4 carry complete code because the
framework is novel and everything else depends on its exact shape. Tasks 5–13 are
specified — files, options, ordered behavior, required assertions — but do not spell
out every formatter line, because they are applications of patterns Tasks 1–4
establish and four near-identical `/stats` subcommands would be pure repetition. If
the executing engineer wants task-level code for those, generate it per task at
execution time rather than reading it from here.

**Type consistency.** `CommandContext` and `AsyncReply` method names are used
identically in Tasks 1, 3, 4, and 6–12. `FakeCommandContext` implements the full
`CommandContext` interface, including `getOptionAsUserId`, `getParentChannelId`,
and `getThreadName`, so it stays substitutable as handlers grow.

**Known risk.** The JDA API surface listed under "Before You Start" is unverified
by compilation. Confirm it before Task 1 — a wrong method name there propagates
into fifteen files.



