package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.discord.icon.IconText;
import com.ako.dbuff.service.discord.icon.TestIcons;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportIconsTest {
  private static final HeroConstant ANTIMAGE =
      new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage");

  private final PlayerGameStatisticRepo stats = mock(PlayerGameStatisticRepo.class);
  private final ReplayPlayerRepo replay = mock(ReplayPlayerRepo.class);
  private final HeroKits kits = mock(HeroKits.class);
  private final ConstantsManagers constants = mock(ConstantsManagers.class);

  ReportIconsTest() {
    when(kits.hero(1L)).thenReturn(Optional.of(ANTIMAGE));
    when(stats.findAllByMatchId(1L))
        .thenReturn(
            List.of(PlayerMatchStatisticDomain.builder().playerSlot(0L).heroId(1L).build()));
    when(constants.getAllAbilityConstants())
        .thenReturn(Map.of("antimage_blink", AbilityConstant.builder().dname("Blink").build()));
    when(constants.getItemConstantMap())
        .thenReturn(Map.of("blink", ItemConstant.builder().dname("Blink Dagger").build()));
    when(constants.getHeroConstantMap()).thenReturn(Map.of("1", ANTIMAGE));
  }

  private ReportHeroNames heroNames(IconService icons) {
    return new ReportHeroNames(stats, replay, kits, icons);
  }

  private ConstantAbilityLabels labels(IconService icons) {
    return new ConstantAbilityLabels(constants, icons);
  }

  @Test
  void heroNames_putTheIconBeforeTheHeroOnlyInTheReportMap() {
    ReportHeroNames names = heroNames(TestIcons.on());

    assertThat(names.iconBySlot(1L)).containsEntry(0L, TestIcons.heroMention() + "Anti-Mage");
    assertThat(names.bySlot(1L)).containsEntry(0L, "Anti-Mage");
    assertThat(names.heroName(1L)).isEqualTo(TestIcons.heroMention() + "Anti-Mage");
  }

  @Test
  void heroNames_areUnchangedWhileTheSwitchIsOff() {
    ReportHeroNames names = heroNames(TestIcons.off());

    assertThat(names.iconBySlot(1L)).isEqualTo(names.bySlot(1L));
    assertThat(names.heroName(1L)).isEqualTo("Anti-Mage");
  }

  @Test
  void trackedPlayerLabelKeepsTheNameAndHeroShape() {
    String hero = heroNames(TestIcons.on()).iconBySlot(1L).get(0L);

    assertThat(ReportHeroNames.label("Anton", hero))
        .isEqualTo("Anton (" + TestIcons.heroMention() + "Anti-Mage)");
  }

  @Test
  void labels_putTheIconOfTheAbilityOrItemBeforeTheName() {
    ConstantAbilityLabels labels = labels(TestIcons.on());

    assertThat(labels.label("antimage_blink")).isEqualTo("Blink");
    assertThat(labels.label("item_blink")).isEqualTo("Blink Dagger");
    assertThat(labels.decorated("antimage_blink")).isEqualTo(TestIcons.abilityMention() + "Blink");
    assertThat(labels.decorated("item_blink")).isEqualTo(TestIcons.itemMention() + "Blink Dagger");
  }

  @Test
  void labels_areUnchangedWhileTheSwitchIsOff() {
    ConstantAbilityLabels labels = labels(TestIcons.off());

    assertThat(labels.decorated("antimage_blink")).isEqualTo("Blink");
    assertThat(labels.decorated("item_blink")).isEqualTo("Blink Dagger");
  }

  @Test
  void draftBoard_showsHeroAndSkillIconsAndReadsAsBeforeWithoutThem() {
    SectionView withIcons = draftBoard(TestIcons.on());
    SectionView without = draftBoard(TestIcons.off());

    assertThat(withIcons.bullets().getFirst().lines())
        .anyMatch(line -> line.contains(TestIcons.heroMention() + "Anti-Mage"));
    assertThat(withIcons.bullets().getFirst().lines())
        .anyMatch(line -> line.contains(TestIcons.abilityMention() + "Blink"));
    assertThat(without.bullets().stream().flatMap(b -> b.lines()).toList())
        .noneMatch(line -> line.contains("<:"));
    assertThat(
            withIcons.bullets().stream()
                .flatMap(b -> b.lines())
                .map(
                    line ->
                        line.replace(TestIcons.heroMention(), "")
                            .replace(TestIcons.abilityMention(), ""))
                .toList())
        .isEqualTo(without.bullets().stream().flatMap(b -> b.lines()).toList());
  }

  @Test
  void reasonEvidenceThatStartsWithTheHeroKeepsTheIconOnce() {
    ReasonWording wording = new ReasonWording(labels(TestIcons.on()));
    String hero = TestIcons.heroMention() + "Anti-Mage";
    var reason =
        com.ako.dbuff.dao.model.FightReasonDomain.builder()
            .scope("PLAYER")
            .code("SOMETHING_NEW")
            .evidence("Anti-Mage was late")
            .build();

    assertThat(wording.phrase(reason, hero)).isEqualTo(hero + " was late");
  }

  @Test
  void iconText_removesOnlyTheIconPrefixAtTheStart() {
    String hero = TestIcons.heroMention() + "Anti-Mage";

    assertThat(IconText.withoutLeadingIcon(hero)).isEqualTo("Anti-Mage");
    assertThat(IconText.withoutLeadingIcon(IconKind.HERO.getPlaceholder() + " Axe"))
        .isEqualTo("Axe");
    String inside = "Anton (" + hero + ") said <:h_x:1> and ⚔️ is a glyph";
    assertThat(IconText.withoutLeadingIcon(inside)).isEqualTo(inside);
    assertThat(IconText.withoutLeadingIcon("<:custom:5> Axe")).isEqualTo("<:custom:5> Axe");
  }

  @Test
  void iconText_neverCutsInsideAnEmojiTag() {
    String text = "ab <:h_antimage:11> cd";

    assertThat(IconText.safeEnd(text, 8)).isEqualTo(3);
    assertThat(IconText.safeEnd(text, 4)).isEqualTo(3);
    assertThat(IconText.safeEnd(text, 19)).isEqualTo(19);
    assertThat(IconText.safeEnd(text, 2)).isEqualTo(2);
    assertThat(IconText.safeEnd(text, 0)).isZero();
    assertThat(IconText.safeEnd(text, 99)).isEqualTo(text.length());
    assertThat(IconText.safeEnd("no tag < here", 8)).isEqualTo(8);
    assertThat(IconText.splitIndex("<:h_antimage:11>" + "x".repeat(10), 5)).isEqualTo(16);
  }

  @Test
  void iconText_neverCutsAUnicodePlaceholderOrItsVariationSelector() {
    String hero = IconKind.HERO.getPlaceholder();
    String item = IconKind.ITEM.getPlaceholder();
    String text = "ab " + hero + " cd " + item + " ef";
    int heroStart = 3;
    int heroEnd = heroStart + hero.length();

    for (int cut = heroStart + 1; cut < heroEnd; cut++) {
      assertThat(IconText.safeEnd(text, cut))
          .as("cut %s inside the hero", cut)
          .isEqualTo(heroStart);
    }
    assertThat(IconText.safeEnd(text, heroEnd)).isEqualTo(heroEnd);
    String itemText = "x" + item + "y";
    assertThat(IconText.safeEnd(itemText, 2)).isEqualTo(1);
    assertThat(IconText.safeEnd(itemText, 1 + item.length())).isEqualTo(1 + item.length());
    assertThat(IconText.splitIndex(hero + "x".repeat(10), 1)).isEqualTo(hero.length());
    assertThat(IconText.splitIndex(item + "x", 1)).isEqualTo(item.length());
    assertThat(IconText.splitIndex("\uD83D\uDC68\u200D\uD83D\uDC69x", 1)).isEqualTo(5);
    assertThat(IconText.splitIndex("<:h_antimage:11>x", 3)).isEqualTo(16);
  }

  @Test
  void iconText_neverSplitsASurrogatePairOrAJoinerSequence() {
    String pair = "a\uD83D\uDE00b";
    assertThat(IconText.safeEnd(pair, 2)).isEqualTo(1);
    assertThat(IconText.safeEnd(pair, 3)).isEqualTo(3);

    String variation = "a\u2694\uFE0Fb";
    assertThat(IconText.safeEnd(variation, 2)).isEqualTo(1);

    String family = "a\uD83D\uDC68\u200D\uD83D\uDC69b";
    assertThat(IconText.safeEnd(family, 3)).isEqualTo(1);
    assertThat(IconText.safeEnd(family, 4)).isEqualTo(1);
    assertThat(IconText.safeEnd(family, 5)).isEqualTo(1);
    assertThat(IconText.safeEnd(family, family.length() - 1)).isEqualTo(family.length() - 1);
  }

  @Test
  void lineCap_holdsWithIconsAndNeverSplitsATag() {
    String tag = "<:h_antimage:11>";
    String line = tag + tag + tag + tag + tag + tag;

    for (int max = 8; max < line.length(); max++) {
      String cut = LineText.cut(line, max);
      assertThat(cut.length()).isLessThanOrEqualTo(max);
      assertThat(cut.replace(tag, "").replace("...", "")).isEmpty();
    }
  }

  @Test
  void componentsCards_stayWithinTheTextAndComponentCapsWithIcons() {
    List<String> bullets =
        IntStream.range(0, 120)
            .mapToObj(
                i ->
                    TestIcons.heroMention()
                        + "Anti-Mage "
                        + TestIcons.abilityMention()
                        + "Blink "
                        + "x".repeat(60))
            .toList();
    SectionView view = new SectionView("Many", null, Accent.INFO, bullets, null);

    List<ReportMessage> cards = new ComponentsViewRenderer(new PlainViewRenderer()).render(view);

    assertThat(cards).isNotEmpty();
    for (ReportMessage card : cards) {
      var c = (ReportMessage.Components) card;
      assertThat(c.textLength()).isLessThanOrEqualTo(MessageLimits.CARD_TEXT);
      assertThat(c.componentCount()).isLessThanOrEqualTo(MessageLimits.CARD_COMPONENTS);
    }
  }

  @Test
  void aSingleLongLineWithIconsIsCutBeforeATagBreaks() {
    String line = ("x".repeat(40) + TestIcons.heroMention()).repeat(200);
    SectionView view = new SectionView("Long", null, Accent.INFO, List.of(line), null);

    List<ReportMessage> cards = new ComponentsViewRenderer(new PlainViewRenderer()).render(view);

    for (ReportMessage card : cards) {
      var fallback = ((ReportMessage.Components) card).fallback();
      assertThat(fallback.replace(TestIcons.heroMention(), "")).doesNotContain("<:");
    }
  }

  @Test
  void draftBoard_withFortyIconedAbilitiesStaysWithinEveryLimit() {
    IconService icons = TestIcons.onWithAbilities(80);
    HeroDraftPickRepo heroRepo = mock(HeroDraftPickRepo.class);
    AbilityDraftPickRepo skillRepo = mock(AbilityDraftPickRepo.class);
    AbilityDraftPoolRepo poolRepo = mock(AbilityDraftPoolRepo.class);
    when(heroRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                HeroDraftPickDomain.builder()
                    .matchId(1L)
                    .heroId(1L)
                    .playerSlot(0L)
                    .turnNumber(1)
                    .pickTick(30)
                    .clockSeconds(-400.0)
                    .build()));
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            IntStream.range(0, 40)
                .mapToObj(
                    i ->
                        AbilityDraftPickDomain.builder()
                            .pickOrder(i)
                            .turnNumber(i + 1)
                            .playerSlot(0L)
                            .abilityName("antimage_blink_" + i)
                            .build())
                .toList());
    when(poolRepo.findByMatchId(1L))
        .thenReturn(
            IntStream.range(40, 80)
                .mapToObj(
                    i ->
                        AbilityDraftPoolDomain.builder()
                            .poolIndex(i)
                            .abilityName("antimage_blink_" + i)
                            .build())
                .toList());
    PlainViewRenderer plain = new PlainViewRenderer();
    DraftBoardSection section =
        new DraftBoardSection(
            List.of(
                new AbilityDraftBoard(
                    heroRepo,
                    skillRepo,
                    poolRepo,
                    mock(com.ako.dbuff.dao.repo.PlayerGameStatisticRepo.class),
                    new AbilityDraftProfile(),
                    labels(icons),
                    heroNames(icons),
                    mock(com.ako.dbuff.service.replay.report.chart.SkillIconImages.class),
                    mock(com.ako.dbuff.service.replay.report.chart.TableRenderer.class))),
            plain);
    SectionView view =
        section
            .view(new ReplayReportInput(1L, abilityDraftMatch(), Set.of(), Map.of(), 3))
            .orElseThrow();

    List<String> lines = plain.lines(view).stream().flatMap(l -> l.lines()).toList();
    List<ReportMessage> cards = new ComponentsViewRenderer(plain).render(view);

    assertThat(lines).allMatch(line -> line.length() < MessageLimits.PLAIN_TEXT);
    assertThat(String.join("\n", lines).split("<:a_antimage_blink_\\d+:\\d+>", -1).length - 1)
        .isEqualTo(80);
    assertThat(view.bullets()).allMatch(b -> b.length() <= 3000);
    for (ReportMessage card : cards) {
      var c = (ReportMessage.Components) card;
      assertThat(c.textLength()).isLessThanOrEqualTo(MessageLimits.CARD_TEXT);
      assertThat(c.componentCount()).isLessThanOrEqualTo(MessageLimits.CARD_COMPONENTS);
      assertThat(c.fallback()).doesNotContain("…");
    }
  }

  private SectionView draftBoard(IconService icons) {
    HeroDraftPickRepo heroRepo = mock(HeroDraftPickRepo.class);
    AbilityDraftPickRepo skillRepo = mock(AbilityDraftPickRepo.class);
    AbilityDraftPoolRepo poolRepo = mock(AbilityDraftPoolRepo.class);
    when(heroRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                HeroDraftPickDomain.builder()
                    .matchId(1L)
                    .heroId(1L)
                    .playerSlot(0L)
                    .turnNumber(1)
                    .pickTick(30)
                    .clockSeconds(-400.0)
                    .build()));
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPickDomain.builder()
                    .pickOrder(1)
                    .turnNumber(1)
                    .playerSlot(0L)
                    .abilityName("antimage_blink")
                    .build()));
    when(poolRepo.findByMatchId(1L)).thenReturn(List.of());
    DraftBoardSection section =
        new DraftBoardSection(
            List.of(
                new AbilityDraftBoard(
                    heroRepo,
                    skillRepo,
                    poolRepo,
                    mock(com.ako.dbuff.dao.repo.PlayerGameStatisticRepo.class),
                    new AbilityDraftProfile(),
                    labels(icons),
                    heroNames(icons),
                    mock(com.ako.dbuff.service.replay.report.chart.SkillIconImages.class),
                    mock(com.ako.dbuff.service.replay.report.chart.TableRenderer.class))),
            new PlainViewRenderer());
    return section
        .view(new ReplayReportInput(1L, abilityDraftMatch(), Set.of(), Map.of(), 3))
        .orElseThrow();
  }

  private static com.ako.dbuff.dao.model.MatchDomain abilityDraftMatch() {
    com.ako.dbuff.dao.model.MatchDomain match = new com.ako.dbuff.dao.model.MatchDomain();
    match.setGameModeId(18L);
    return match;
  }
}
