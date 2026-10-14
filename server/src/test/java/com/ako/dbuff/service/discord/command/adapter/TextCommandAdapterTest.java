package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.TextCommandProperties;
import com.ako.dbuff.service.discord.command.impl.MatchCommand;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
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
  void aiWithAModelRoutesTheModelArgumentThroughTheParserAndTheAdapter() {
    MatchCommand real =
        Mockito.spy(
            new MatchCommand(
                mock(MatchRepo.class),
                mock(DbufInstanceConfigRepository.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    Mockito.doNothing().when(real).execute(any(), any());
    TextCommandProperties properties = new TextCommandProperties();
    properties.setTextPrefix("~");
    TextCommandAdapter adapter =
        new TextCommandAdapter(new CommandRegistry(List.of(real), properties), properties);

    adapter.onMessageReceived(messageEvent("~ai gpt-4o"));

    ArgumentCaptor<CommandContext> context = ArgumentCaptor.forClass(CommandContext.class);
    verify(real, timeout(2000)).execute(org.mockito.ArgumentMatchers.eq("ai"), context.capture());
    assertThat(context.getValue().getOption("model")).isEqualTo("gpt-4o");
  }

  @Test
  void defaultPrefixStillAnswersBang() {
    adapterWithPrefix("!").onMessageReceived(messageEvent("!rerun"));

    verify(match, timeout(2000)).execute(any(), any());
  }
}
