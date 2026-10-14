package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.config.OpenAiConfig;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A small match: Dire (slots 128 and 129, tracked) beat Radiant (slot 0) in 34:12. */
final class ReasonsFixtures {
  static final long MATCH_ID = 900L;

  private ReasonsFixtures() {}

  static MatchDomain match() {
    return MatchDomain.builder()
        .id(MATCH_ID)
        .radiantWin(false)
        .duration(2052L)
        .radiantScore(20L)
        .direScore(31L)
        .build();
  }

  static ReplayReportInput input() {
    return new ReplayReportInput(MATCH_ID, match(), Set.of(128L, 129L), Map.of(), 3);
  }

  static OpenAiConfig openAi() {
    return new OpenAiConfig();
  }

  static MatchBasicFacts basicFacts() {
    PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
    when(statRepo.findAllByMatchId(MATCH_ID))
        .thenReturn(
            List.of(
                stat(0L, 4L, 9L, 2L, 380L, 410L, 15200L),
                stat(128L, 11L, 3L, 14L, 612L, 700L, 31800L),
                stat(129L, 2L, 6L, 20L, 301L, 420L, 14100L)));
    PlayerRoleRepo roleRepo = mock(PlayerRoleRepo.class);
    when(roleRepo.findByMatchId(MATCH_ID))
        .thenReturn(
            List.of(
                PlayerRoleDomain.builder().playerSlot(128L).role(1).build(),
                PlayerRoleDomain.builder().playerSlot(129L).role(5).build()));
    ReportHeroNames names = mock(ReportHeroNames.class);
    when(names.bySlot(anyLong()))
        .thenReturn(Map.of(0L, "Axe", 128L, "Faceless Void", 129L, "Crystal Maiden"));
    return new MatchBasicFacts(statRepo, roleRepo, names);
  }

  private static PlayerMatchStatisticDomain stat(
      long slot, long k, long d, long a, long gpm, long xpm, long netWorth) {
    return PlayerMatchStatisticDomain.builder()
        .matchId(MATCH_ID)
        .playerSlot(slot)
        .kills(k)
        .deaths(d)
        .assists(a)
        .goldPerMin(gpm)
        .xpPerMin(xpm)
        .netWorth(netWorth)
        .build();
  }

  static ReplayReportSection section(String name, String verdict, String... bullets) {
    return new ReplayReportSection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return 1;
      }

      @Override
      public ReportDetail minimumDetail() {
        return ReportDetail.STANDARD;
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return List.of(bullets);
      }

      @Override
      public Optional<SectionView> view(ReplayReportInput input) {
        return bullets.length == 0
            ? Optional.empty()
            : Optional.of(new SectionView(name, verdict, Accent.INFO, List.of(bullets), null));
      }
    };
  }

  @SuppressWarnings("unchecked")
  static ObjectProvider<ReplayReportSection> provider(ReplayReportSection... sections) {
    ObjectProvider<ReplayReportSection> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(call -> List.of(sections).stream());
    return provider;
  }

  static AiMatchFactsBuilder builder(ReplayReportSection... sections) {
    return new AiMatchFactsBuilder(basicFacts(), new SectionFacts(provider(sections)));
  }
}
