package com.ako.dbuff.service.replay;

import java.nio.file.Path;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Bound from {@code replay.processing.*}. Replay work is off until explicitly enabled. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.processing")
public class ReplayProcessingProperties {
  private boolean enabled = false;

  /** Replays parsed at the same time; each one needs most of the heap budget on the prod host. */
  private int maxConcurrent = 1;

  /** Matches taken per job run, processed one after another. */
  private int batchSize = 5;

  private String cron = "0 3/10 * * * ?";

  /** Valve deletes replays after a while; older matches are not tried. */
  private int maxAgeDays = 90;

  private DataSize maxReplayBytes = DataSize.ofMegabytes(150);
  private DataSize maxDemoBytes = DataSize.ofMegabytes(500);
  private Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "dbuff-replays");

  /** A match whose details were mapped this recently is taken before everything else. */
  private int freshWindowHours = 6;

  /** A retry of a match mapped within this many hours goes before older new matches. */
  private int retryPriorityHours = 24;

  /** ...but only while it has had at most this many attempts. */
  private int retryPriorityAttempts = 2;

  private Backlog backlog = new Backlog();

  private int maxAttempts = 5;

  /** A run where an extractor failed is tried again fewer times: the cause is often permanent. */
  private int maxPartialAttempts = 2;

  private int retryBackoffHours = 1;
  private int maxDurationMin = 120;
  private int maxParseSeconds = 120;
  private int leaseMinutes = 30;

  /** Bumped when the extractor set changes, which makes finished matches eligible again. */
  private int parserVersion = 1;

  /** Game modes whose replays are analysed: Ability Draft and All Draft. */
  private List<Long> supportedModes = List.of(18L, 22L);

  /** Arguments: cluster, match id, salt. */
  private String urlTemplate = "http://replay%d.valve.net/570/%d_%d.dem.bz2";

  private int connectTimeoutSeconds = 10;
  private int readTimeoutSeconds = 120;

  /** Whole download, headers and body; a stalled body is cut off when this passes. */
  private int maxDownloadSeconds = 300;

  /** Old retries and matches mapped long ago; worked on only when nothing else is waiting. */
  @Data
  public static class Backlog {
    private boolean enabled = false;

    /** Backlog matches taken per job run. */
    private int batchSize = 2;
  }
}
