package com.ako.dbuff.service.replay.report.view;

import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSeamTest {
  private final PlainViewRenderer plain = new PlainViewRenderer();
  private final ComponentsViewRenderer components = new ComponentsViewRenderer(plain);
  private final ImageAttachment image = new ImageAttachment("g.png", new byte[] {1});

  private SectionView withImage(List<String> bullets) {
    return new SectionView("Title", "Verdict", Accent.NEUTRAL, bullets, null, image);
  }

  private SectionView withoutImage(List<String> bullets) {
    return new SectionView("Title", "Verdict", Accent.NEUTRAL, bullets, null);
  }

  @Test
  void aViewWithoutAnImageRendersAsBefore() {
    assertThat(withoutImage(List.of("a")).image()).isNull();
    assertThat(plain.render(withoutImage(List.of("a"))))
        .containsExactly(new ReportMessage.Plain("**Title**\na"));
    ReportMessage.Components card =
        (ReportMessage.Components) components.render(withoutImage(List.of("a"))).get(0);
    assertThat(card.images()).isEmpty();
    assertThat(card.componentCount()).isEqualTo(4);
    assertThat(card.fallback()).isEqualTo("**Title**\nVerdict\na");
  }

  @Test
  void plainModePostsTheTextWithTheFile() {
    assertThat(plain.render(withImage(List.of())))
        .containsExactly(new ReportMessage.Plain("**Title**", image));
  }

  @Test
  void componentsModePutsAMediaGalleryOnTheAttachmentAndCountsItsComponents() {
    ReportMessage.Components card =
        (ReportMessage.Components) components.render(withImage(List.of())).get(0);

    Container container = (Container) card.components().get(0);
    MediaGallery gallery =
        container.getComponents().stream()
            .filter(MediaGallery.class::isInstance)
            .map(MediaGallery.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(gallery.getItems().get(0).getUrl()).isEqualTo("attachment://g.png");
    assertThat(card.images()).containsExactly(image);
    assertThat(card.componentCount()).isEqualTo(5);
    assertThat(card.fallback()).isEqualTo("**Title**\nVerdict");
  }

  @Test
  void anImageOnlyViewStillRenders() {
    SectionView view = new SectionView("", null, Accent.INFO, List.of(), null, image);

    assertThat(plain.render(view)).containsExactly(new ReportMessage.Plain("", image));
    assertThat(components.render(view)).hasSize(1);
  }

  @Test
  void onlyTheFirstPageOfASplitViewCarriesTheImage() {
    List<String> many = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "line " + i).toList();

    List<ReportMessage> pages = components.render(withImage(many));

    assertThat(pages.size()).isGreaterThan(1);
    assertThat(((ReportMessage.Components) pages.get(0)).images()).containsExactly(image);
    assertThat(((ReportMessage.Components) pages.get(1)).images()).isEmpty();
    pages.forEach(
        p ->
            assertThat(((ReportMessage.Components) p).componentCount())
                .isLessThanOrEqualTo(MessageLimits.CARD_COMPONENTS));
  }

  @Test
  void packedCardsKeepTheirImagesAndANoteKeepsTheImageOfAPlainMessage() {
    List<ReportMessage> packed =
        components.renderAll(List.of(withImage(List.of()), withoutImage(List.of("b"))));

    assertThat(packed).hasSize(1);
    assertThat(((ReportMessage.Components) packed.get(0)).images()).containsExactly(image);
    ReportMessage.Components noted =
        (ReportMessage.Components) packed.get(0).withNote("\n_(1 more left out)_").orElseThrow();
    assertThat(noted.images()).containsExactly(image);
    assertThat(
            ((ReportMessage.Plain) new ReportMessage.Plain("a", image).withNote("n").orElseThrow())
                .image())
        .isSameAs(image);
  }
}
