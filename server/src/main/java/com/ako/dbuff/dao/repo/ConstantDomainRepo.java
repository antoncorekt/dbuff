package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ConstantDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstantDomainRepo extends JpaRepository<ConstantDomain, String> {}
