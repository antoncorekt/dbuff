package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerDomain;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepo
    extends JpaRepository<PlayerDomain, Long>, PagingAndSortingRepository<PlayerDomain, Long> {
  String name(String name);

  Page<PlayerDomain> findAll(Pageable pageable);

  /**
   * Finds a player by their name.
   *
   * @param name The player's name
   * @return Optional containing the player if found
   */
  Optional<PlayerDomain> findByName(String name);

  /**
   * Finds players by their OpenDota account IDs (the {@code id} column, not the {@code name}
   * primary key).
   *
   * @param ids the account IDs
   * @return the matching players
   */
  @Query("SELECT p FROM PlayerDomain p WHERE p.id IN :ids")
  List<PlayerDomain> findByAccountIds(@Param("ids") Collection<Long> ids);

  /**
   * Finds players whose name matches the given POSIX regular expression, case-insensitively (the
   * Postgres {@code ~*} operator). The pattern is unanchored, so a plain substring such as {@code
   * termit} matches "TERMIT", and {@code .*MIT} matches any name containing "MIT".
   *
   * @param pattern the case-insensitive regular expression
   * @return the matching players
   */
  @Query(value = "SELECT * FROM player_domain WHERE name ~* :pattern", nativeQuery = true)
  List<PlayerDomain> findByNameMatchingRegex(@Param("pattern") String pattern);
}
