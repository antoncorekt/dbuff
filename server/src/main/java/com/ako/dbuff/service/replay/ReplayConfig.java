package com.ako.dbuff.service.replay;

import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ReplayConfig {
  /** Limits how many replays are parsed at the same time; shared by every entry point. */
  @Bean
  public Semaphore replayParseSemaphore(ReplayProcessingProperties properties) {
    log.info("Replay parse semaphore with {} permits", properties.getMaxConcurrent());
    return new Semaphore(properties.getMaxConcurrent());
  }
}
