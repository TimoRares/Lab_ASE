package com.instakill.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creeaza (daca lipsesc) indexurile de care depind interogarile de analytics.
 * Hibernate nu a creat indexul din LikeEntity pe SQLite, iar fara index fiecare numarare
 * de like-uri per postare ar parcurge intreg tabelul.
 */
@Component
@Order(0)
public class AnalyticsIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIndexInitializer.class);

    private final JdbcTemplate jdbc;

    public AnalyticsIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_likes_post ON likes (post_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id)");
        } catch (RuntimeException e) {
            log.warn("Nu s-au putut crea indexurile pentru analytics: {}", e.toString());
        }
    }
}
