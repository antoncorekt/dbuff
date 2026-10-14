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

  private final ComponentsViewRenderer renderer = new ComponentsViewRenderer();

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
  void aNoteIsAddedOnlyWhileItFits() {
    ReportMessage.Components small =
        (ReportMessage.Components) renderer.render(view(Accent.INFO, null, null, "a")).get(0);

    ReportMessage noted = small.withNote("\n_(2 more left out)_");

    assertThat(((ReportMessage.Components) noted).components()).hasSize(2);
    assertThat(((TextDisplay) ((ReportMessage.Components) noted).components().get(1)).getContent())
        .isEqualTo("_(2 more left out)_");
    ReportMessage.Components full =
        (ReportMessage.Components)
            renderer.render(view(Accent.INFO, null, null, "z".repeat(9000))).get(0);
    assertThat(full.withNote("\n_(1 more left out)_")).isSameAs(full);
  }

  @Test
  void aPlainNoteIsAddedOnlyWhileItFits() {
    assertThat(new ReportMessage.Plain("abc").withNote("!"))
        .isEqualTo(new ReportMessage.Plain("abc!"));
    ReportMessage.Plain full = new ReportMessage.Plain("a".repeat(MessageLimits.PLAIN_TEXT));
    assertThat(full.withNote("!")).isSameAs(full);
  }
}
