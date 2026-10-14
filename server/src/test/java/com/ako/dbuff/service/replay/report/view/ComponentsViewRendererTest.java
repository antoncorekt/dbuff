package com.ako.dbuff.service.replay.report.view;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentsViewRendererTest {

  private final ComponentsViewRenderer renderer =
      new ComponentsViewRenderer(new PlainViewRenderer());

  private static SectionView view(
      Accent accent, String verdict, String footnote, String... bullets) {
    return new SectionView("Title", verdict, accent, List.of(bullets), footnote);
  }

  private static Container container(ReportMessage message) {
    return (Container) ((ReportMessage.Components) message).components().get(0);
  }

  private static List<String> texts(ReportMessage message) {
    return container(message).getComponents().stream()
        .filter(c -> c.getType() == net.dv8tion.jda.api.components.Component.Type.TEXT_DISPLAY)
        .map(c -> ((TextDisplay) c).getContent())
        .collect(Collectors.toList());
  }

  @Test
  void buildsOneAccentContainerWithTitleVerdictDividerAndBullets() {
    List<ReportMessage> messages =
        renderer.render(view(Accent.WIN, "Radiant won 3 of 4", null, "a", "b"));

    assertThat(messages).hasSize(1);
    Container card = container(messages.get(0));
    assertThat(card.getAccentColorRaw()).isEqualTo(Accent.WIN.rgb());
    assertThat(texts(messages.get(0)))
        .containsExactly("**Title**\nRadiant won 3 of 4", "- a", "- b");
    assertThat(card.getComponents().get(1)).isInstanceOf(Separator.class);
    assertThat(((ReportMessage.Components) messages.get(0)).componentCount()).isEqualTo(5);
  }

  @Test
  void accentColoursDifferPerAccent() {
    assertThat(Accent.values()).extracting(Accent::rgb).doesNotHaveDuplicates();
    assertThat(
            container(renderer.render(view(Accent.LOSS, null, null, "a")).get(0))
                .getAccentColorRaw())
        .isEqualTo(Accent.LOSS.rgb());
  }

  @Test
  void showsAFootnoteLastAndSkipsTheVerdictLineWhenNull() {
    List<ReportMessage> messages = renderer.render(view(Accent.INFO, null, "small print", "a"));

    assertThat(texts(messages.get(0))).containsExactly("**Title**", "- a", "-# small print");
  }

  @Test
  void aViewWithoutBulletsIsAHeaderOnlyCardWithoutDivider() {
    List<ReportMessage> messages = renderer.render(view(Accent.INFO, "Nothing to add", null));

    assertThat(messages).hasSize(1);
    assertThat(container(messages.get(0)).getComponents()).hasSize(1);
  }

  @Test
  void anEmptyViewRendersNothing() {
    assertThat(renderer.render(new SectionView("", null, Accent.INFO, List.of(), null))).isEmpty();
  }

  @Test
  void splitsOnTheComponentCountAndRepeatsTheTitleWithThePage() {
    String[] bullets = IntStream.range(0, 80).mapToObj(i -> "b" + i).toArray(String[]::new);

    List<ReportMessage> messages = renderer.render(view(Accent.INFO, null, null, bullets));

    assertThat(messages).hasSize(3);
    assertThat(texts(messages.get(0)).get(0)).isEqualTo("**Title (1/3)**");
    assertThat(texts(messages.get(2)).get(0)).isEqualTo("**Title (3/3)**");
    messages.forEach(
        m ->
            assertThat(((ReportMessage.Components) m).componentCount())
                .isLessThanOrEqualTo(MessageLimits.COMPONENTS_COUNT));
    assertThat(messages.stream().mapToLong(m -> texts(m).size() - 1).sum()).isEqualTo(80);
  }

  @Test
  void splitsOnTheTextLimit() {
    String[] bullets = IntStream.range(0, 10).mapToObj(i -> "x".repeat(900)).toArray(String[]::new);

    List<ReportMessage> messages = renderer.render(view(Accent.INFO, null, null, bullets));

    assertThat(messages.size()).isGreaterThan(1);
    messages.forEach(
        m ->
            assertThat(((ReportMessage.Components) m).textLength())
                .isLessThanOrEqualTo(MessageLimits.COMPONENTS_TEXT));
    assertThat(texts(messages.get(1)).get(0)).startsWith("**Title (2/");
  }

  @Test
  void cutsABulletLongerThanAMessageWithAnEllipsis() {
    List<ReportMessage> messages = renderer.render(view(Accent.INFO, null, null, "y".repeat(9000)));

    assertThat(messages).hasSize(1);
    ReportMessage.Components only = (ReportMessage.Components) messages.get(0);
    assertThat(only.textLength()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_TEXT);
    assertThat(texts(only).get(1)).endsWith("…");
  }

  @Test
  void aNoteAlwaysFitsTheLastPageOfAViewThatFilledItsBudgets() {
    String[] bullets = IntStream.range(0, 80).mapToObj(i -> "x".repeat(300)).toArray(String[]::new);
    List<ReportMessage> messages = renderer.render(view(Accent.INFO, null, null, bullets));

    messages.forEach(m -> assertThat(m.withNote("\n_(12 more left out)_")).isPresent());
    ReportMessage.Components noted =
        (ReportMessage.Components) messages.get(0).withNote("\n_(2 more left out)_").orElseThrow();
    assertThat(((TextDisplay) noted.components().get(1)).getContent())
        .isEqualTo("_(2 more left out)_");
    assertThat(noted.fallback()).endsWith("_(2 more left out)_");
  }

  @Test
  void aNoteThatCannotFitIsRefusedForTheCapToPostSeparately() {
    ReportMessage.Components full =
        new ReportMessage.Components(List.of(), MessageLimits.COMPONENTS_TEXT, 1, "");

    assertThat(full.withNote("\n_(1 more left out)_")).isEmpty();
    ReportMessage.Plain plain = new ReportMessage.Plain("a".repeat(MessageLimits.PLAIN_TEXT));
    assertThat(plain.withNote("!")).isEmpty();
    assertThat(new ReportMessage.Plain("abc").withNote("!"))
        .contains(new ReportMessage.Plain("abc!"));
  }

  @Test
  void anUntitledMultiPageViewStillShowsThePageMarker() {
    String[] bullets = IntStream.range(0, 80).mapToObj(i -> "b" + i).toArray(String[]::new);

    List<ReportMessage> messages =
        renderer.render(new SectionView("", null, Accent.INFO, List.of(bullets), null));

    assertThat(messages).hasSize(3);
    assertThat(texts(messages.get(1)).get(0)).isEqualTo("**(2/3)**");
  }

  @Test
  void boundsAHugeTitleAndVerdictSoTheItemsKeepTheirRoom() {
    SectionView view =
        new SectionView("T".repeat(4500), "V".repeat(4500), Accent.INFO, List.of("a", "b"), null);

    List<ReportMessage> messages = renderer.render(view);

    assertThat(messages).hasSize(1);
    ReportMessage.Components only = (ReportMessage.Components) messages.get(0);
    assertThat(only.textLength()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_TEXT);
    assertThat(texts(only)).hasSize(3).element(0).asString().contains("…");
  }

  @Test
  void packsConsecutiveCardsWhileTheyFitAndStartsTheNextMessageWhenTheyDoNot() {
    SectionView small = view(Accent.INFO, null, null, "a", "b");
    SectionView big =
        view(
            Accent.WIN,
            null,
            null,
            IntStream.range(0, 3).mapToObj(i -> "x".repeat(1310)).toArray(String[]::new));

    List<ReportMessage> messages = renderer.renderAll(List.of(small, small, big, big, small));

    assertThat(messages).hasSize(3);
    assertThat(((ReportMessage.Components) messages.get(0)).components()).hasSize(2);
    assertThat(((ReportMessage.Components) messages.get(1)).components()).hasSize(1);
    assertThat(((ReportMessage.Components) messages.get(2)).components()).hasSize(2);
    messages.forEach(
        m -> {
          ReportMessage.Components c = (ReportMessage.Components) m;
          assertThat(c.textLength()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_TEXT);
          assertThat(c.componentCount()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_COUNT);
        });
  }

  @Test
  void packingCountsEveryNestedComponent() {
    SectionView ten =
        view(
            Accent.INFO,
            null,
            null,
            IntStream.range(0, 12).mapToObj(i -> "b").toArray(String[]::new));

    List<ReportMessage> messages = renderer.renderAll(List.of(ten, ten, ten));

    assertThat(messages).hasSize(2);
  }

  @Test
  void aViewWithoutTitleAndVerdictReservesNoHeader() {
    String[] bullets = IntStream.range(0, 37).mapToObj(i -> "b").toArray(String[]::new);

    List<ReportMessage> messages =
        renderer.render(new SectionView("", null, Accent.INFO, List.of(bullets), null));

    assertThat(messages).hasSize(1);
    ReportMessage.Components only = (ReportMessage.Components) messages.get(0);
    assertThat(container(only).getComponents()).hasSize(37);
    assertThat(only.componentCount()).isEqualTo(38);
  }

  @Test
  void aMultiLineEntryStaysOneBlockWithoutABulletCharacter() {
    List<ReportMessage> messages =
        renderer.render(view(Accent.INFO, null, null, "one\ntwo", "three"));

    assertThat(texts(messages.get(0))).containsExactly("**Title**", "one\ntwo", "- three");
  }

  @Test
  void theFallbackKeepsTheVerdictUnderTheTitle() {
    ReportMessage.Components card =
        (ReportMessage.Components)
            renderer.render(view(Accent.WIN, "Radiant won 1 of 2", "small", "a")).get(0);

    assertThat(card.fallback()).isEqualTo("**Title**\nRadiant won 1 of 2\na\nsmall");
  }
}
