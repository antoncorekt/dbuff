package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.constant.ConstantPrecacheStarter;
import com.ako.dbuff.service.replay.clarity.ClarityReplayParser;
import com.ako.dbuff.service.replay.file.ReplayDecompressor;
import java.util.List;
import java.util.concurrent.Semaphore;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReplayWiringTest {

  @MockBean private JDA jda;
  @MockBean private ConstantPrecacheStarter constantPrecacheStarter;

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayParser parser;
  @Autowired private List<ReplayDecompressor> decompressors;
  @Autowired private List<ReplayExtractor> extractors;
  @Autowired private ReplayProcessingProperties properties;

  @Autowired
  @Qualifier("replayParseSemaphore")
  private Semaphore semaphore;

  @Test
  void theReplayPipelineIsWiredAndOffUntilEnabled() {
    assertThat(runService).isNotNull();
    assertThat(AopUtils.getTargetClass(parser)).isEqualTo(ClarityReplayParser.class);
    assertThat(decompressors).hasSize(2);
    assertThat(extractors).extracting(ReplayExtractor::name).contains("players");
    assertThat(properties.isEnabled()).isFalse();
    assertThat(semaphore.availablePermits()).isEqualTo(properties.getMaxConcurrent());
  }
}
