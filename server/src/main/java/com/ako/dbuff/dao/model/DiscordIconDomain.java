package com.ako.dbuff.dao.model;

import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.discord.icon.IconStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One application emoji of the bot for a hero, ability or item. The key is the constant's key (hero
 * name without {@code npc_dota_hero_}, ability name, item name without {@code item_}).
 */
@Entity
@Table(
    name = "discord_icon",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_discord_icon_kind_key",
            columnNames = {"kind", "iconKey"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscordIconDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "discord_icon_seq")
  @SequenceGenerator(
      name = "discord_icon_seq",
      sequenceName = "discord_icon_seq",
      allocationSize = 50)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(length = 10, nullable = false)
  private IconKind kind;

  @Column(nullable = false)
  private String iconKey;

  private Long emojiId;

  @Column(length = 32)
  private String emojiName;

  /** An emoji replaced by a newer one whose deletion failed; a later sync deletes it. */
  private Long staleEmojiId;

  /** SHA-256 of the 128 px PNG that was uploaded. */
  @Column(length = 64)
  private String imageHash;

  /** The CDN path the image came from, to skip the download while the constants do not change. */
  @Column(length = 512)
  private String sourcePath;

  @Enumerated(EnumType.STRING)
  @Column(length = 10, nullable = false)
  private IconStatus status;

  @Column(length = 500)
  private String error;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
