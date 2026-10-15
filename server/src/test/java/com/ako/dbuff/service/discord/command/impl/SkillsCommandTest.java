package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillAnswer;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillLookup;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatNames;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatProperties;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatWording;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsCommandTest {
  private ConstantNameResolver heroResolver;
  private SkillNameIndex index;
  private SkillLookup lookup;
  private SkillsCommand command;

  @BeforeEach
  void setUp() {
    heroResolver = Mockito.mock(ConstantNameResolver.class);
    index = Mockito.mock(SkillNameIndex.class);
    lookup = Mockito.mock(SkillLookup.class);
    SkillStatNames names =
        new SkillStatNames() {
          @Override
          public String hero(long heroId) {
            return "Axe";
          }

          @Override
          public String skill(long abilityId) {
            return abilityId == 1 ? "Storm Bolt" : "Thunder Clap";
          }
        };
    command =
        new SkillsCommand(
            heroResolver,
            index,
            lookup,
            new SkillStatWording(names),
            names,
            new SkillStatProperties());
    Mockito.when(index.heroes("Axe")).thenReturn(List.of(2L));
    Mockito.when(index.heroes("Axx")).thenReturn(List.of());
    Mockito.when(index.heroes("Twin")).thenReturn(List.of(2L, 5L));
    Mockito.when(heroResolver.suggestHero("Axx")).thenReturn(Optional.of("Axe"));
    Mockito.when(index.resolve("Storm Bolt")).thenReturn(List.of(1L));
    Mockito.when(index.resolve("Thunder Clap")).thenReturn(List.of(3L));
    Mockito.when(index.resolve("Storm Bolt ")).thenReturn(List.of(1L));
    Mockito.when(index.resolve("Nothing")).thenReturn(List.of());
    Mockito.when(index.resolve("Twin Skill")).thenReturn(List.of(7L, 8L));
    Mockito.when(index.internalName(7L)).thenReturn(Optional.of("a_twin"));
    Mockito.when(index.internalName(8L)).thenReturn(Optional.of("b_twin"));
    Mockito.when(index.suggest("Nothing")).thenReturn(Optional.empty());
  }

  private static SkillPickStatDomain row(
      SkillStatKind kind, long first, Long second, long games, long wins) {
    return SkillPickStatDomain.builder()
        .kind(kind)
        .firstId(first)
        .secondId(second)
        .games(games)
        .wins(wins)
        .windowMonths(12)
        .build();
  }

  private static FakeCommandContext context(String... keyValues) {
    FakeCommandContext.Builder builder = FakeCommandContext.builder();
    for (int i = 0; i < keyValues.length; i += 2) {
      builder.option(keyValues[i], keyValues[i + 1]);
    }
    return builder.build();
  }

  private void assertRefused(FakeCommandContext context, String... fragments) {
    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains(fragments);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(lookup);
  }

  @Test
  void definitionUsesTheSharedOptionNamesAndAutocompletesAllThree() {
    List<OptionData> options = command.getDefinition().getOptions();

    assertThat(options)
        .extracting(OptionData::getName)
        .containsExactlyElementsOf(SkillsOptions.ALL);
    assertThat(options).allMatch(OptionData::isAutoComplete);
    assertThat(options).noneMatch(OptionData::isRequired);
  }

  @Test
  void noOptionIsRefusedWithExamplesBeforeAcknowledging() {
    FakeCommandContext context = context();

    command.execute(null, context);

    assertRefused(context, "hero:Axe", "skill:Storm Bolt with:");
  }

  @Test
  void heroTogetherWithASkillIsRefused() {
    FakeCommandContext context = context("hero", "Axe", "skill", "Storm Bolt");

    command.execute(null, context);

    assertRefused(context, "hero alone");
  }

  @Test
  void withoutSkillIsRefused() {
    FakeCommandContext context = context("with", "Thunder Clap");

    command.execute(null, context);

    assertRefused(context, "`with:` needs `skill:`");
  }

  @Test
  void anUnknownHeroIsRefusedWithASuggestion() {
    FakeCommandContext context = context("hero", "Axx");

    command.execute(null, context);

    assertRefused(context, "No hero called `Axx`", "Did you mean `Axe`?");
  }

  @Test
  void anUnknownSkillIsRefused() {
    FakeCommandContext context = context("skill", "Nothing");

    command.execute(null, context);

    assertRefused(context, "No skill called `Nothing`");
  }

  @Test
  void anUnknownSecondSkillIsRefused() {
    FakeCommandContext context = context("skill", "Storm Bolt", "with", "Nothing");

    command.execute(null, context);

    assertRefused(context, "No skill called `Nothing`");
  }

  @Test
  void theSameSkillTwiceIsRefused() {
    FakeCommandContext context = context("skill", "Storm Bolt", "with", "Storm Bolt ");

    command.execute(null, context);

    assertRefused(context, "same skill");
  }

  @Test
  void aHeroAcknowledgesThenPostsItsWinRateAndSkillsWithCounts() {
    Mockito.when(index.skillsOfHero(2L)).thenReturn(List.of(1L, 3L));
    Mockito.when(lookup.hero(2L, List.of(1L, 3L)))
        .thenReturn(
            new SkillAnswer(
                row(SkillStatKind.HERO, 2, null, 120, 65),
                List.of(
                    row(SkillStatKind.HERO_SKILL, 2, 1L, 40, 22),
                    row(SkillStatKind.HERO_SKILL, 2, 3L, 3, 1))));
    FakeCommandContext context = context("hero", "Axe");

    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getAcknowledgeSummary()).contains("Axe");
    assertThat(context.getPosts()).hasSize(1);
    assertThat(context.getPosts().get(0))
        .isEqualTo(
            """
            Ability Draft, the last 12 months
            Axe: 54% in 120 games
            Axe's skills, most reliable win rate first:
            Axe with Storm Bolt: 55% in 40 games
            Axe with Thunder Clap: 33% in only 3 games\
            """);
  }

  @Test
  void aHeroWithNoGamesIsOneLine() {
    Mockito.when(index.skillsOfHero(2L)).thenReturn(List.of(1L));
    Mockito.when(lookup.hero(2L, List.of(1L)))
        .thenReturn(new SkillAnswer(row(SkillStatKind.HERO, 2, null, 0, 0), List.of()));
    FakeCommandContext context = context("hero", "Axe");

    command.execute(null, context);

    assertThat(context.getPosts().get(0))
        .isEqualTo("Ability Draft, the last 12 months\nAxe: no games in the last 12 months");
  }

  @Test
  void aHeroWhoseSkillsHaveNoGamesSaysSo() {
    Mockito.when(index.skillsOfHero(2L)).thenReturn(List.of(1L));
    Mockito.when(lookup.hero(2L, List.of(1L)))
        .thenReturn(new SkillAnswer(row(SkillStatKind.HERO, 2, null, 9, 4), List.of()));
    FakeCommandContext context = context("hero", "Axe");

    command.execute(null, context);

    assertThat(context.getPosts().get(0)).contains("None of the skills listed for Axe");
  }

  @Test
  void severalHeroesWithTheNameAreListedNotGuessed() {
    FakeCommandContext context = context("hero", "Twin");

    command.execute(null, context);

    assertRefused(context, "matches several heroes", "Axe, Axe");
  }

  @Test
  void severalSkillsWithTheNameAreListedByInternalName() {
    FakeCommandContext context = context("skill", "Twin Skill");

    command.execute(null, context);

    assertRefused(context, "several skills", "a_twin, b_twin");
  }

  @Test
  void typedTextIsEchoedWithoutBackticksOrMentions() {
    FakeCommandContext context = context("hero", "`@everyone` x");
    Mockito.when(index.heroes("`@everyone` x")).thenReturn(List.of());

    command.execute(null, context);

    String reply = context.getEphemeralReplies().get(0);
    assertThat(reply).contains("No hero called `@\u200beveryone x`");
    assertThat(reply).doesNotContain("@everyone");
  }

  @Test
  void aKeyWithNoNameIsNamedInTheRefusal() {
    FakeCommandContext context = context("skill", "", "with", "Thunder Clap");

    command.execute(null, context);

    assertRefused(context, "`skill:` has no name after it");
  }

  @Test
  void loneTextTellsThatAnOptionNameIsNeeded() {
    FakeCommandContext context = context("text", "Axe");

    command.execute(null, context);

    assertRefused(context, "`Axe` needs an option name in front");
  }

  @Test
  void aHeroWithNoKnownSkillsStillShowsItsOwnRate() {
    Mockito.when(index.skillsOfHero(2L)).thenReturn(List.of());
    Mockito.when(lookup.hero(2L, List.of()))
        .thenReturn(new SkillAnswer(row(SkillStatKind.HERO, 2, null, 5, 2), List.of()));
    FakeCommandContext context = context("hero", "Axe");

    command.execute(null, context);

    assertThat(context.getPosts().get(0))
        .contains("Axe: 40% in only 5 games", "No skill list is known for Axe.");
  }

  @Test
  void aSkillPostsItsRateAndTheHeroItIsBestOn() {
    Mockito.when(lookup.skill(1L))
        .thenReturn(
            new SkillAnswer(
                row(SkillStatKind.SKILL, 1, null, 80, 42),
                List.of(row(SkillStatKind.HERO_SKILL, 2, 1L, 12, 8))));
    FakeCommandContext context = context("skill", "Storm Bolt");

    command.execute(null, context);

    assertThat(context.getPosts().get(0))
        .isEqualTo(
            """
            Ability Draft, the last 12 months
            Storm Bolt: 53% in 80 games
            Best on: Axe with Storm Bolt: 67% in 12 games\
            """);
  }

  @Test
  void aSkillNobodyHeldSaysSoWithoutABestHero() {
    Mockito.when(lookup.skill(1L))
        .thenReturn(new SkillAnswer(row(SkillStatKind.SKILL, 1, null, 0, 0), List.of()));
    FakeCommandContext context = context("skill", "Storm Bolt");

    command.execute(null, context);

    assertThat(context.getPosts().get(0))
        .contains("Storm Bolt: no games in the last 12 months")
        .doesNotContain("Best on");
  }

  @Test
  void aPairPostsGamesRateAndLiftInWords() {
    SkillPickStatDomain pair = row(SkillStatKind.SKILL_PAIR, 1, 3L, 14, 8);
    pair.setFirstGamesWithout(100L);
    pair.setFirstWinsWithout(52L);
    pair.setSecondGamesWithout(100L);
    pair.setSecondWinsWithout(52L);
    Mockito.when(lookup.pair(1L, 3L)).thenReturn(new SkillAnswer(pair, List.of()));
    FakeCommandContext context = context("skill", "Storm Bolt", "with", "Thunder Clap");

    command.execute(null, context);

    assertThat(context.getPosts().get(0))
        .endsWith(
            "Storm Bolt with Thunder Clap: 57% in 14 games, 5 points above what each gives"
                + " alone");
  }

  @Test
  void aFailedQueryIsReportedInTheThreadAfterAcknowledging() {
    Mockito.when(lookup.skill(1L)).thenThrow(new IllegalStateException("db down"));
    FakeCommandContext context = context("skill", "Storm Bolt");

    command.execute(null, context);

    assertThat(context.getAcknowledgeSummary()).isNotNull();
    assertThat(context.getFailures())
        .singleElement()
        .asString()
        .contains("Try again later")
        .doesNotContain("db down");
  }

  @Test
  void textArgumentsFollowTheSameOptionNames() {
    assertThat(command.parseTextArguments("skills", "skill:Storm", "Bolt with:Thunder Clap"))
        .isEqualTo(Map.of("skill", "Storm Bolt", "with", "Thunder Clap"));
    assertThat(command.parseTextArguments("skills", "hero:Anti-Mage", ""))
        .isEqualTo(Map.of("hero", "Anti-Mage"));
    assertThat(command.parseTextArguments("skills", "HERO:Axe", ""))
        .isEqualTo(Map.of("hero", "Axe"));
  }

  @Test
  void textWithoutKnownKeysYieldsNoOptionsSoValidationExplains() {
    assertThat(command.parseTextArguments("skills", null, "")).isEmpty();
    assertThat(command.parseTextArguments("skills", "Axe", "")).isEqualTo(Map.of("text", "Axe"));
    assertThat(command.parseTextArguments("skills", "skill:", "with:Thunder Clap"))
        .isEqualTo(Map.of("skill", "", "with", "Thunder Clap"));
  }
}
