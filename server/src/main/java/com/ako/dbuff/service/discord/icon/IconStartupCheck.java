package com.ako.dbuff.service.discord.icon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Runs {@link IconRegistryCheck} once after start-up, off the start-up thread. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.icons.enabled", havingValue = "true")
public class IconStartupCheck {
  private final IconRegistryCheck check;

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    Thread thread =
        new Thread(
            () -> {
              try {
                check.verify();
              } catch (RuntimeException e) {
                log.warn("Icon start-up check failed, emoji are used as stored", e);
              }
            },
            "icon-startup-check");
    thread.setDaemon(true);
    thread.start();
  }
}
