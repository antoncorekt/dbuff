package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PictureViewsTest {
  private static ReplayReportInput input() {
    return new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3);
  }

  @Test
  void buildsOncePerDelivery() {
    PictureViews pictures = new PictureViews();
    AtomicInteger builds = new AtomicInteger();
    ReplayReportInput input = input();

    pictures.view(input, i -> built(builds));
    pictures.view(input, i -> built(builds));

    assertThat(builds).hasValue(1);
  }

  @Test
  void buildsAgainForAnotherDelivery() {
    PictureViews pictures = new PictureViews();
    AtomicInteger builds = new AtomicInteger();

    pictures.view(input(), i -> built(builds));
    pictures.view(input(), i -> built(builds));

    assertThat(builds).hasValue(2);
  }

  @Test
  void drawReturnsThePicture() {
    ImageAttachment image = new ImageAttachment("a.png", new byte[] {1});

    assertThat(PictureViews.draw("Any", 1L, () -> Optional.of(image))).contains(image);
  }

  @Test
  void drawFallsBackToEmptyWhenDrawingFails() {
    assertThat(
            PictureViews.draw(
                "Any",
                1L,
                () -> {
                  throw new IllegalStateException("boom");
                }))
        .isEmpty();
  }

  private static Optional<SectionView> built(AtomicInteger builds) {
    builds.incrementAndGet();
    return Optional.of(new SectionView("t", "v", Accent.NEUTRAL, List.of(), null, null));
  }
}
