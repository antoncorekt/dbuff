package com.ako.dbuff.service.discord.command.adapter;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordPermissionsTest {

  private Member member;
  private TextChannel channel;

  @BeforeEach
  void setUp() {
    member = Mockito.mock(Member.class);
    channel = Mockito.mock(TextChannel.class);
  }

  @Test
  void nullMember_directMessage_cannotManage() {
    assertThat(DiscordPermissions.canManageChannel(null, channel)).isFalse();
  }

  @Test
  void memberWithNeitherPermission_cannotManage() {
    assertThat(DiscordPermissions.canManageChannel(member, channel)).isFalse();
  }

  @Test
  void manageServerAlone_isEnough() {
    Mockito.when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);

    assertThat(DiscordPermissions.canManageChannel(member, channel)).isTrue();
  }

  @Test
  void manageChannelOnTheChannel_isEnough() {
    Mockito.when(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true);

    assertThat(DiscordPermissions.canManageChannel(member, channel)).isTrue();
  }

  @Test
  void inAThread_isJudgedByTheParentChannel() {
    ThreadChannel thread = Mockito.mock(ThreadChannel.class);
    IThreadContainerUnion parent = Mockito.mock(IThreadContainerUnion.class);
    Mockito.when(thread.getParentChannel()).thenReturn(parent);
    Mockito.when(member.hasPermission(parent, Permission.MANAGE_CHANNEL)).thenReturn(true);

    assertThat(DiscordPermissions.canManageChannel(member, thread)).isTrue();
  }

  @Test
  void inAThread_managingOnlyTheThreadItselfIsNotEnough() {
    ThreadChannel thread = Mockito.mock(ThreadChannel.class);
    IThreadContainerUnion parent = Mockito.mock(IThreadContainerUnion.class);
    Mockito.when(thread.getParentChannel()).thenReturn(parent);
    Mockito.when(member.hasPermission(thread, Permission.MANAGE_CHANNEL)).thenReturn(true);

    assertThat(DiscordPermissions.canManageChannel(member, thread)).isFalse();
  }
}
