package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.discord.icon.IconStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordIconRepo extends JpaRepository<DiscordIconDomain, Long> {

  Optional<DiscordIconDomain> findByKindAndIconKey(IconKind kind, String iconKey);

  List<DiscordIconDomain> findByStatus(IconStatus status);

  long countByStatus(IconStatus status);
}
