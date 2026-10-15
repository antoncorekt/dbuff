package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillAnswer;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillLookup;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatNames;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatProperties;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatWording;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * {@code /skills} (text alias: the command name, {@code ~skills} with that prefix) — how a hero, a
 * skill or two skills held together did in the recent Ability Draft games. It only reads, from the
 * same counts and wording as the match report.
 *
 * <p>Three questions: {@code hero:} alone, {@code skill:} alone, {@code skill:} with {@code with:}.
 * Anything else is refused before the command acknowledges, in a message that names the fix.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillsCommand implements DbuffCommand {
  private static final Pattern OPTION_KEY =
      Pattern.compile(
          "(?:^|\\s)(" + String.join("|", SkillsOptions.ALL) + "):", Pattern.CASE_INSENSITIVE);

  private final ConstantNameResolver heroResolver;
  private final SkillNameIndex skillIndex;
  private final SkillLookup lookup;
  private final SkillStatWording wording;
  private final SkillStatNames names;
  private final SkillStatProperties properties;

  @Override
  public String getName() {
    return "skills";
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash(
            getName(), "Win rate of a hero, a skill or two skills together in Ability Draft")
        .addOptions(
            new OptionData(
                OptionType.STRING, SkillsOptions.HERO, "The hero and its skills", false, true),
            new OptionData(
                OptionType.STRING,
                SkillsOptions.SKILL,
                "One skill, or the first of a pair",
                false,
                true),
            new OptionData(
                OptionType.STRING,
                SkillsOptions.WITH,
                "The second skill of a pair (needs skill)",
                false,
                true));
  }

  /** {@code ~skills skill:Storm Bolt with:Thunder Clap}: each known key starts a value. */
  @Override
  public Map<String, String> parseTextArguments(String alias, String subcommand, String arguments) {
    String text = ((subcommand == null ? "" : subcommand) + " " + arguments).strip();
    Map<String, String> options = new HashMap<>();
    Matcher keys = OPTION_KEY.matcher(text);
    String key = null;
    int valueStart = 0;
    while (keys.find()) {
      store(options, key, text.substring(valueStart, keys.start()));
      key = keys.group(1).toLowerCase();
      valueStart = keys.end();
    }
    store(options, key, text.substring(valueStart));
    return options;
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    String hero = value(context, SkillsOptions.HERO);
    String skill = value(context, SkillsOptions.SKILL);
    String with = value(context, SkillsOptions.WITH);

    Optional<String> problem = problem(hero, skill, with);
    if (problem.isPresent()) {
      context.replyEphemeral("❌ " + problem.get());
      return;
    }
    if (hero != null) {
      answerHero(context, hero);
    } else {
      answerSkills(context, skill, with);
    }
  }

  private Optional<String> problem(String hero, String skill, String with) {
    if (hero == null && skill == null && with == null) {
      return Optional.of(
          "Name a hero or a skill, e.g. `/skills hero:Axe`, `/skills skill:Storm Bolt` or"
              + " `/skills skill:Storm Bolt with:Thunder Clap`.");
    }
    if (hero != null && (skill != null || with != null)) {
      return Optional.of(
          "Ask for a hero alone or for skills, not both: drop `hero:` or the skills.");
    }
    if (with != null && skill == null) {
      return Optional.of(
          "`with:` needs `skill:` too, e.g. `/skills skill:Storm Bolt with:" + with + "`.");
    }
    return Optional.empty();
  }

  private void answerHero(CommandContext context, String heroInput) {
    Optional<Long> heroId = resolveHero(heroInput);
    if (heroId.isEmpty()) {
      context.replyEphemeral(unknown("hero", heroInput, heroResolver.suggestHero(heroInput)));
      return;
    }
    String heroName = names.hero(heroId.get());
    AsyncReply reply = acknowledge(context, heroName);
    work(reply, heroName, () -> heroLines(heroId.get(), heroName));
  }

  private void answerSkills(CommandContext context, String skillInput, String withInput) {
    Optional<Long> first = skillIndex.resolve(skillInput);
    if (first.isEmpty()) {
      context.replyEphemeral(unknown("skill", skillInput, skillIndex.suggest(skillInput)));
      return;
    }
    if (withInput == null) {
      String title = names.skill(first.get());
      AsyncReply reply = acknowledge(context, title);
      work(reply, title, () -> skillLines(first.get()));
      return;
    }
    Optional<Long> second = skillIndex.resolve(withInput);
    if (second.isEmpty()) {
      context.replyEphemeral(unknown("skill", withInput, skillIndex.suggest(withInput)));
      return;
    }
    if (first.get().equals(second.get())) {
      context.replyEphemeral("❌ `skill:` and `with:` are the same skill; name two different ones.");
      return;
    }
    String title = names.skill(first.get()) + " with " + names.skill(second.get());
    AsyncReply reply = acknowledge(context, title);
    work(reply, title, () -> pairLines(first.get(), second.get()));
  }

  private List<String> heroLines(long heroId, String heroName) {
    List<Long> skills = skillIndex.skillsOfHero(heroId);
    SkillAnswer answer = lookup.hero(heroId, skills);
    if (answer.details().isEmpty()) {
      return List.of(
          header(), describe(answer.subject()), "No skill list is known for " + heroName + ".");
    }
    return List.of(
        header(),
        describe(answer.subject()),
        heroName + "'s skills, most reliable win rate first:",
        describeAll(answer.details()));
  }

  private List<String> skillLines(long skillId) {
    SkillAnswer answer = lookup.skill(skillId);
    if (answer.details().isEmpty()) {
      return List.of(header(), describe(answer.subject()));
    }
    return List.of(
        header(), describe(answer.subject()), "Best on: " + describe(answer.details().get(0)));
  }

  private List<String> pairLines(long first, long second) {
    return List.of(header(), describe(lookup.pair(first, second).subject()));
  }

  private String header() {
    int months = properties.getWindowMonths();
    return "Ability Draft, " + (months == 1 ? "the last month" : "the last " + months + " months");
  }

  private String describe(SkillPickStatDomain row) {
    return wording.describe(row);
  }

  private String describeAll(List<SkillPickStatDomain> rows) {
    return String.join("\n", rows.stream().map(this::describe).toList());
  }

  private void work(AsyncReply reply, String title, Supplier<List<String>> lines) {
    try {
      reply.post(String.join("\n", lines.get()));
    } catch (RuntimeException e) {
      log.warn("Skill statistics for {} failed", title, e);
      reply.fail("⚠️ Could not fetch statistics for " + title + ": " + e.getMessage());
    }
  }

  private AsyncReply acknowledge(CommandContext context, String title) {
    return context.acknowledge("📊 " + title + " in Ability Draft…", title + " skills");
  }

  private Optional<Long> resolveHero(String input) {
    return heroResolver.resolveHeroes(Set.of(input)).resolvedIds().stream().findFirst();
  }

  private static String unknown(String what, String typed, Optional<String> suggestion) {
    return "❌ No "
        + what
        + " called `"
        + typed
        + "`."
        + suggestion.map(name -> " Did you mean `" + name + "`?").orElse("");
  }

  private static String value(CommandContext context, String option) {
    String raw = context.getOption(option);
    return raw == null || raw.isBlank() ? null : raw.strip();
  }

  private static void store(Map<String, String> options, String key, String value) {
    if (key != null && !value.isBlank()) {
      options.put(key, value.strip());
    }
  }
}
