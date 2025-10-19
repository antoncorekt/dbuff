package com.ako.dbuff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@EnableCaching
public class DbuffApplication {

  public static void main(String[] args) {
    SpringApplication.run(DbuffApplication.class, args);
  }
}
