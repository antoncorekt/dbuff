package com.ako.dbuff.service.replay.clarity;

import java.util.List;

/** Something that reads the players' entities on every step of the sampling grid. */
interface SampleObserver {
  /**
   * @param gridTick the tick this sample stands for
   */
  void sampled(long gridTick, List<Controllers.View> players);
}
