package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AskThreadDomain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AskThreadRepo extends JpaRepository<AskThreadDomain, String> {}
