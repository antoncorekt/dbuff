package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.TextCommandProperties;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TextCommandAdapterTest {

  private final DbuffCommand match = mock(DbuffCommand.class);

  private TextCommandAdapter adapterWithPrefix(String prefix) {
    when(match.getName()).thenReturn("match");
    when(match.getDefinition()).thenReturn(Commands.slash("match", "Match tools"));
    when(match.getTextAliases()).thenReturn(List.of("rerun"));
    when(match.resolveTextSubcommand(any(), any())).thenReturn(null);
    TextCommandProperties properties = new TextCommandProperties();
    properties.setTextPrefix(prefix);
    return new TextCommandAdapter(new CommandRegistry(List.of(match), properties), properties);
  }

  private MessageReceivedEvent messageEvent(String content) {
    User author = mock(User.class);
    when(author.isBot()).thenReturn(false);
    Message message = mock(Message.class);
    when(message.getContentRaw()).thenReturn(content);
    MessageReceivedEvent event = mock(MessageReceivedEvent.class);
    when(event.getAuthor()).thenReturn(author);
    when(event.getMessage()).thenReturn(message);
    return event;
  }

  @Test
  void customPrefixIgnoresTheDefaultBang() {
    adapterWithPrefix("~").onMessageReceived(messageEvent("!rerun"));

    verify(match, never()).execute(any(), any());
  }

  @Test
  void customPrefixAnswersItsOwnCommands() {
    adapterWithPrefix("~").onMessageReceived(messageEvent("~rerun"));

    verify(match, timeout(2000)).execute(any(), any());
  }

  @Test
  void defaultPrefixStillAnswersBang() {
    adapterWithPrefix("!").onMessageReceived(messageEvent("!rerun"));

    verify(match, timeout(2000)).execute(any(), any());
  }
}
