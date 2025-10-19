package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepo
    extends JpaRepository<PlayerDomain, Long>, PagingAndSortingRepository<PlayerDomain, Long> {
  String name(String name);

  Page<PlayerDomain> findAll(Pageable pageable);
}
