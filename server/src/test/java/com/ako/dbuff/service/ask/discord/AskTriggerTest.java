package com.ako.dbuff.service.ask.discord;

import com.ako.dbuff.service.ask.discord.AskTrigger.Decision;
import com.ako.dbuff.service.ask.discord.AskTrigger.MessageFacts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskTriggerTest {

  private static MessageFacts channel(String content) {
    return new MessageFacts(false, false, false, content, false, "!");
  }

  @Test
  void aPlainChannelMessage_isANewQuestion() {
    assertThat(AskTrigger.decide(channel("who has the best win rate?")))
        .isEqualTo(Decision.NEW_QUESTION);
  }

  @Test
  void aMessageInAnAskThread_isAFollowUp() {
    assertThat(AskTrigger.decide(new MessageFacts(false, true, true, "and last week?", false, "!")))
        .isEqualTo(Decision.FOLLOW_UP);
  }

  @Test
  void aTextCommand_isIgnored() {
    assertThat(AskTrigger.decide(channel("!stats Anton"))).isEqualTo(Decision.IGNORE);
  }

  @Test
  void aSlashMessage_isIgnored() {
    assertThat(AskTrigger.decide(channel("/x"))).isEqualTo(Decision.IGNORE);
  }

  @Test
  void aMessageWithAnImage_isIgnored() {
    assertThat(AskTrigger.decide(new MessageFacts(false, false, false, "look", true, "!")))
        .isEqualTo(Decision.IGNORE);
  }

  @Test
  void aBot_isIgnored() {
    assertThat(AskTrigger.decide(new MessageFacts(true, false, false, "hello", false, "!")))
        .isEqualTo(Decision.IGNORE);
  }

  @Test
  void aThreadThatIsNotAnAskThread_isIgnored() {
    assertThat(AskTrigger.decide(new MessageFacts(false, true, false, "hello", false, "!")))
        .isEqualTo(Decision.IGNORE);
  }

  @Test
  void blankContent_isIgnored() {
    assertThat(AskTrigger.decide(channel("   "))).isEqualTo(Decision.IGNORE);
    assertThat(AskTrigger.decide(channel(null))).isEqualTo(Decision.IGNORE);
  }

  @Test
  void aCommandInAnAskThread_isIgnored() {
    assertThat(AskTrigger.decide(new MessageFacts(false, true, true, "!stats", false, "!")))
        .isEqualTo(Decision.IGNORE);
  }
}
