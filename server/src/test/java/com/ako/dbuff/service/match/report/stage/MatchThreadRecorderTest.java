package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MatchThreadRecorderTest {

  @Test
  void aLegacyThreadReusingAStagedRowLeavesNoStageOrFactsBehind() {
    MatchReportThreadRepo repo = Mockito.mock(MatchReportThreadRepo.class);
    MatchReportThreadDomain existing =
        MatchReportThreadDomain.builder()
            .matchId(1L)
            .instanceId("i")
            .stage(MatchReportStage.PARSED)
            .modeName("ability draft")
            .wins(1)
            .losses(0)
            .durationSeconds(2100L)
            .build();
    Mockito.when(repo.findByMatchIdAndInstanceId(1L, "i")).thenReturn(Optional.of(existing));
    ThreadChannel thread = Mockito.mock(ThreadChannel.class);
    Message header = Mockito.mock(Message.class);

    new MatchThreadRecorder(repo)
        .remember(
            MatchDomain.builder().id(1L).build(),
            DbufInstanceConfigDomain.builder().id("i").discordChannelId("c").build(),
            thread,
            header,
            null,
            null);

    assertThat(existing.getStage()).isNull();
    assertThat(existing.getModeName()).isNull();
    assertThat(existing.getWins()).isNull();
    assertThat(existing.getLosses()).isNull();
    assertThat(existing.getDurationSeconds()).isNull();
    Mockito.verify(repo).save(existing);
  }

  @Test
  void advancingToTheFirstStageRunsNoQuery() {
    MatchReportThreadRepo repo = Mockito.mock(MatchReportThreadRepo.class);

    boolean moved =
        new MatchThreadRecorder(repo)
            .advance(
                MatchReportThreadDomain.builder().id(1L).build(),
                MatchReportStage.REQUESTED,
                new MatchFacts(1L, null, new MatchFacts.Result(0, 0), null, null, null));

    assertThat(moved).isFalse();
    Mockito.verifyNoInteractions(repo);
  }
}
