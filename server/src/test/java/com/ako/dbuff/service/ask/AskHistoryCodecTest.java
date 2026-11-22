package com.ako.dbuff.service.ask;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaStopReason;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.ask.AskTestSupport.message;
import static com.ako.dbuff.service.ask.AskTestSupport.text;
import static com.ako.dbuff.service.ask.AskTestSupport.thinking;
import static com.ako.dbuff.service.ask.AskTestSupport.toolUse;
import static org.assertj.core.api.Assertions.assertThat;

class AskHistoryCodecTest {

  private final AskHistoryCodec codec = new AskHistoryCodec();

  @Test
  void assistantTurnWithThinkingRoundTripsUnchanged() {
    BetaMessage response =
        message(
            BetaStopReason.TOOL_USE,
            thinking("think about it"),
            text("let me look"),
            toolUse("toolu_1", "player_stats", Map.of("players", java.util.List.of("Ann"))));

    String json = codec.toJson(response);
    BetaMessageParam restored = codec.fromJson(json);

    assertThat(codec.toJson(response.toParam())).isEqualTo(json);
    assertThat(json).contains("think about it").contains("sig-think about it");
    assertThat(codec.toJson(restored)).isEqualTo(json);
  }

  @Test
  void userTurnRoundTrips() {
    BetaMessageParam user = BetaMessageParam.ofUser("how many games?");

    assertThat(codec.fromJson(codec.toJson(user))).isEqualTo(user);
  }
}
