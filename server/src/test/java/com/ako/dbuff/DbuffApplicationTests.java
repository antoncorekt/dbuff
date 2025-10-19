package com.ako.dbuff;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;

// @SpringBootTest
class DbuffApplicationTests {

  @Test
  void contextLoads() {

    System.out.println(1757010833000L);

    System.out.println(new Date(1757010833000L));
    LocalDate localDateTime =
        Instant.ofEpochMilli(1757010833000L).atZone(ZoneId.systemDefault()).toLocalDate();
    System.out.println(localDateTime);
    System.out.println(localDateTime.getMonthValue());
    System.out.println(localDateTime.getYear());

    String datetime = "2023-11-19T18:58:21+00:00";
    OffsetDateTime odt = OffsetDateTime.parse(datetime);

    LocalDateTime dateTime = odt.toLocalDateTime();
  }
}
