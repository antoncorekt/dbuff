package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.SectionView;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Per-delivery view cache and the safe drawing of a table picture, shared by picture sections. */
@Slf4j
final class PictureViews {
  static final String NOT_DRAWN = "The picture could not be drawn this time.";
  private static final int DELIVERIES_KEPT = 8;

  private final Cache<ReplayReportInput, Optional<SectionView>> views =
      Caffeine.newBuilder().weakKeys().maximumSize(DELIVERIES_KEPT).build();

  Optional<SectionView> view(
      ReplayReportInput input, Function<ReplayReportInput, Optional<SectionView>> build) {
    return views.get(input, build);
  }

  static Optional<ImageAttachment> draw(
      String what, long matchId, Supplier<Optional<ImageAttachment>> picture) {
    try {
      return picture.get();
    } catch (RuntimeException e) {
      log.warn("{} picture of match {} failed: {}", what, matchId, e.toString(), e);
      return Optional.empty();
    }
  }
}
