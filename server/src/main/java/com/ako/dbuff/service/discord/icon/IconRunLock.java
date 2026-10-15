package com.ako.dbuff.service.discord.icon;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** Keeps a sync and the start-up check from changing the icon table at the same time. */
@Component
public class IconRunLock {
  private final ReentrantLock lock = new ReentrantLock();

  public <T> T exclusively(java.util.function.Supplier<T> action) {
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }
}
