package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.repo.DiscordIconRepo;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IconSyncStartTest {

  @Test
  void secondStartIsRefusedWhileARunIsInProgress() throws Exception {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    IconCandidates candidates = mock(IconCandidates.class);
    when(candidates.of(any(), anyBoolean()))
        .thenAnswer(
            call -> {
              inside.countDown();
              release.await(5, TimeUnit.SECONDS);
              return List.of();
            });
    IconSyncService sync =
        new IconSyncService(
            new IconProperties(),
            mock(DiscordIconRepo.class),
            candidates,
            mock(IconImageSource.class),
            mock(EmojiGateway.class),
            mock(IconService.class));

    assertThat(sync.start(Set.of(IconKind.HERO), false)).isTrue();
    assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(sync.start(Set.of(IconKind.HERO), false)).isFalse();
    release.countDown();
  }
}
