package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchDetailServiceReplayInfoTest {

  private final MatchDetailService service = new MatchDetailService(List.of());

  @Test
  void replace_keepsTheStoredReplayLocationWhenAFetchNoLongerCarriesIt() {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    match.setReplayCluster(7L);
    match.setReplaySalt(99L);

    service.replace(new MatchResponse(), match);

    assertThat(match.getReplayCluster()).isEqualTo(7L);
    assertThat(match.getReplaySalt()).isEqualTo(99L);
  }

  @Test
  void replace_takesANewReplayLocationWhenTheFetchHasOne() {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    match.setReplayCluster(7L);
    match.setReplaySalt(99L);
    MatchResponse response = new MatchResponse();
    response.setCluster(8L);
    response.setReplaySalt(100L);

    service.replace(response, match);

    assertThat(match.getReplayCluster()).isEqualTo(8L);
    assertThat(match.getReplaySalt()).isEqualTo(100L);
  }
}
