package com.ako.dbuff.service.replay;

import java.util.List;

/**
 * What one parse of a replay produced. Extractors read narrow views of it and never touch the
 * parser library.
 */
public record ReplayFacts(
    int clockStartTick, double gameStartTime, List<ReplayPlayerFact> players) {}
