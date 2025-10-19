package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<ItemDomain, Long> {}
