package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.HistoryEntry;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.MatchStats;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.WinLoseStat;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordStatisticFormatterTest {

  private final DiscordStatisticFormatter formatter = new DiscordStatisticFormatter();

  @Test
  void formatPlayer_notFound_returnsHeaderOnly() {
    ExternalPlayerStatisticResponse response =
        ExternalPlayerStatisticResponse.builder().playerName("Ghost").playerId(null).build();

    List<String> messages = formatter.formatPlayer(response);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).contains("Ghost").contains("not found");
  }

  @Test
  void formatPlayer_includesRecordAndHistoryLine() {
    ExternalPlayerStatisticResponse response =
        ExternalPlayerStatisticResponse.builder()
            .playerName("Dendi")
            .playerId(999L)
            .againstStat(WinLoseStat.builder().win(3).lose(2).build())
            .teammateStat(WinLoseStat.builder().win(5).lose(1).build())
            .history(
                List.of(
                    HistoryEntry.builder()
                        .matchId(8795480597L)
                        .matchDate(LocalDate.of(2024, 3, 20))
                        .dotabuffLink("https://www.dotabuff.com/matches/8795480597/builds")
                        .teammate(true)
                        .against(false)
                        .playerWon(true)
                        .matchStats(MatchStats.builder().playerHero("Pudge").build())
                        .build()))
            .build();

    List<String> messages = formatter.formatPlayer(response);

    assertThat(messages).hasSize(1);
    String message = messages.get(0);
    assertThat(message).contains("Dendi").contains("999");
    assertThat(message).contains("3").contains("2").contains("5").contains("1"); // W/L counts
    assertThat(message).contains("8795480597").contains("2024-03-20").contains("Pudge");
    // Link wrapped in <> so Discord suppresses the preview embed.
    assertThat(message).contains("<https://www.dotabuff.com/matches/8795480597/builds>");
  }

  @Test
  void formatPlayer_longHistory_splitsAndCaps() {
    List<HistoryEntry> history =
        IntStream.range(0, 40)
            .mapToObj(
                i ->
                    HistoryEntry.builder()
                        .matchId((long) i)
                        .matchDate(LocalDate.of(2024, 1, 1))
                        .dotabuffLink("https://www.dotabuff.com/matches/" + i + "/builds")
                        .teammate(i % 2 == 0)
                        .against(i % 2 != 0)
                        .playerWon(i % 2 == 0)
                        .matchStats(MatchStats.builder().playerHero("Hero" + i).build())
                        .build())
            .toList();

    ExternalPlayerStatisticResponse response =
        ExternalPlayerStatisticResponse.builder()
            .playerName("Grinder")
            .playerId(1L)
            .againstStat(WinLoseStat.builder().win(0).lose(0).build())
            .teammateStat(WinLoseStat.builder().win(0).lose(0).build())
            .history(history)
            .build();

    List<String> messages = formatter.formatPlayer(response);

    // Only the most recent MAX_HISTORY_ENTRIES are shown, with a truncation note.
    String joined = String.join("\n", messages);
    assertThat(joined).contains("more matches");
    assertThat(messages).allSatisfy(m -> assertThat(m.length()).isLessThanOrEqualTo(1900));
  }
}
