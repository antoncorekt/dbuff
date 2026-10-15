package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.repo.SkillCountRepository;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.SwingSection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The replay pipeline against a real PostgreSQL ({@code SMOKE_PG_URL}, by default the local
 * container on port 5544), with the stand-ins of {@link ReplayPipelineFixture}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=${SMOKE_PG_URL:jdbc:postgresql://localhost:5544/dbuff}",
      "spring.datasource.username=postgres",
      "spring.datasource.password=password",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=update",
      "spring.jpa.show-sql=false",
      "replay.processing.max-attempts=1"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ComponentScan(
    basePackages = "com.ako.dbuff.service.replay",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern =
                ".*\\.(ReplayProcessingJob|ReplayRunJanitor|ReplayQueue|ReplayBatchService|ConstantAbilityNames|ConstantAbilityLabels|ConstantHeroKits|ConstantItemKits|ReplayTempCleaner|ReplayReportService)|.*Test(\\$.*)?"))
@Import({
  ReplayPipelineFixture.Config.class,
  SwingSection.class,
  DeathSection.class,
  FightSection.class,
  MovementSection.class,
  DraftSection.class,
  CompositionSection.class,
  SkillCountRepository.class
})
@TestPropertySource(properties = "replay.processing.enabled=true")
@ExtendWith(LocalDatabase.class)
public @interface ReplayPipelineTest {}
