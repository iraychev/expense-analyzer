package com.iraychev.expenseanalyzer;

import com.iraychev.expenseanalyzer.categorization.CategorizationPipeline;
import com.iraychev.expenseanalyzer.categorization.CategorizationWorker;
import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.ResilientAiCategorizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the whole application against in-memory H2 with dummy secrets, so that bean wiring, the startup
 * initializers and the schema generation are exercised without the production database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wiring;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS expense_analyzer",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "rsa.private-key=classpath:certs/private.pem",
        "rsa.public-key=classpath:certs/public.pem",
        "ai.api.key=dummy",
        "gocardless.api.secret-id=dummy",
        "gocardless.api.secret-key=dummy",
        "categorization.worker.initial-delay=PT1H"})
class ApplicationWiringTest {

    @Autowired private AiCategorizer aiCategorizer;
    @Autowired private CategorizationPipeline pipeline;
    @Autowired private CategorizationWorker worker;

    @Test
    void contextStartsAndTheAiIsWrappedByTheResilientDecorator() {
        assertNotNull(pipeline);
        assertNotNull(worker);
        assertTrue(aiCategorizer instanceof ResilientAiCategorizer);
    }
}
