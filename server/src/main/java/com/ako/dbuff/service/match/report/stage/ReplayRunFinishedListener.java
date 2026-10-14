package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.replay.ReplayRunFinished;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Retitles the match headers when a replay run ends. The Discord calls run on their own virtual
 * thread, so a slow call never holds the parse permit or the batch, and nothing reaches the
 * publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRunFinishedListener {
  private final MatchReportStageService stages;

  @EventListener
  public void onRunFinished(ReplayRunFinished event) {
    if (!stages.enabled()) {
      return;
    }
    Thread.startVirtualThread(() -> retitle(event));
  }

  private void retitle(ReplayRunFinished event) {
    try {
      stages.replayFinished(event.matchId(), event.status(), event.retryLeft());
    } catch (RuntimeException e) {
      log.warn("Replay title of match {} failed: {}", event.matchId(), e.getMessage(), e);
    }
  }
}
