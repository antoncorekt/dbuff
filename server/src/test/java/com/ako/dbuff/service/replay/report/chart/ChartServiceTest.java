package com.ako.dbuff.service.replay.report.chart;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChartServiceTest {
  private final ChartRenderer renderer = mock(ChartRenderer.class);
  private final ChartProperties properties = new ChartProperties();

  @Test
  void drawsOnceAndServesTheSameBytesFromTheCache() {
    when(renderer.render(any())).thenReturn(new byte[] {1});
    ChartService service = new ChartService(renderer, properties);

    byte[] first = service.png("m1", ChartTestSpecs::sample).orElseThrow();
    byte[] second = service.png("m1", ChartTestSpecs::sample).orElseThrow();

    assertThat(second).isSameAs(first);
    verify(renderer, times(1)).render(any());
    service.png("m2", ChartTestSpecs::sample);
    verify(renderer, times(2)).render(any());
  }

  @Test
  void aFailureYieldsNothingAndIsNotCached() {
    when(renderer.render(any()))
        .thenThrow(new ChartRenderException("no fonts", new RuntimeException()))
        .thenReturn(new byte[] {7});
    ChartService service = new ChartService(renderer, properties);

    assertThat(service.png("m1", ChartTestSpecs::sample)).isEmpty();
    assertThat(service.png("m1", ChartTestSpecs::sample)).isPresent();
  }

  @Test
  void aBrokenAwtLinkageErrorYieldsNothingToo() {
    when(renderer.render(any())).thenThrow(new NoClassDefFoundError("sun/awt/X11FontManager"));

    assertThat(new ChartService(renderer, properties).png("m1", ChartTestSpecs::sample)).isEmpty();
  }

  @Test
  void aSecondDrawWaitsForTheLimitAndGivesUpWhenItIsNotFreed() throws Exception {
    properties.setMaxConcurrent(1);
    properties.setWaitSeconds(1);
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger running = new AtomicInteger();
    when(renderer.render(any()))
        .thenAnswer(
            call -> {
              running.incrementAndGet();
              inside.countDown();
              release.await(10, TimeUnit.SECONDS);
              return new byte[] {1};
            });
    ChartService service = new ChartService(renderer, properties);
    Thread first = new Thread(() -> service.png("a", ChartTestSpecs::sample));
    first.start();
    assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

    assertThat(service.png("b", ChartTestSpecs::sample)).isEmpty();
    assertThat(running.get()).isEqualTo(1);

    release.countDown();
    first.join(5000);
    assertThat(service.png("b", ChartTestSpecs::sample)).isPresent();
  }

  @Test
  void theLimitsAreValidated() {
    properties.setCacheMaxEntries(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("cache-max-entries");
    properties.setCacheMaxEntries(10);
    properties.setCacheTtlMinutes(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("cache-ttl-minutes");
    properties.setCacheTtlMinutes(10);
    properties.setMaxConcurrent(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("max-concurrent");
    properties.setMaxConcurrent(2);
    properties.validate();
  }
}
