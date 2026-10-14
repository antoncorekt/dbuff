package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import java.util.Comparator;

/** The one order outlier rows are listed in, for the analysis document and the report alike. */
public final class OutlierRowOrder {
  private OutlierRowOrder() {}

  /** By start, then slot, kind and id; a null sorts last. */
  public static final Comparator<PlayerOutlierDomain> CHRONOLOGICAL =
      Comparator.comparing(
              PlayerOutlierDomain::getStartSeconds, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getPlayerSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getKind, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));
}
