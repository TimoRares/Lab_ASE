package com.instakill.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Toate statisticile se calculeaza direct in SQL (SQLite), nu in Java.
 *
 * Note despre schema:
 *  - timestamp-urile sunt salvate ca milisecunde epoch (integer);
 *  - tipul media nu e o coloana: se deduce din extensia lui image_url
 *    (.mp4/.mov/.webm = video, orice alt URL = photo);
 *  - engagement = like-uri + comentarii.
 */
@Repository
public class AnalyticsRepository {

    /** Inceputul saptamanii (luni) ca text yyyy-MM-dd, pornind de la o coloana in milisecunde. */
    private static String week(String column) {
        return "date(" + column + " / 1000, 'unixepoch', 'weekday 0', '-6 days')";
    }

    private static final String MEDIA_TYPE = """
            CASE WHEN lower(p.image_url) LIKE '%.mp4' OR lower(p.image_url) LIKE '%.mov'
                      OR lower(p.image_url) LIKE '%.webm' THEN 'video' ELSE 'photo' END""";

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsResponse forUser(UUID userId) {
        String id = userId.toString();
        return new AnalyticsResponse(
                weekly(id),
                topPosts(id),
                likeCommentRatio(id),
                averageLikesByMedia(id),
                ranking(id));
    }

    /** 1. Postari, like-uri si comentarii pe saptamana (fiecare grupat dupa data propriei creari). */
    List<AnalyticsResponse.WeeklyStat> weekly(String userId) {
        String sql = """
                SELECT week, SUM(posts), SUM(likes), SUM(comments) FROM (
                    SELECT %s AS week, 1 AS posts, 0 AS likes, 0 AS comments
                    FROM posts p WHERE p.author_id = ?
                  UNION ALL
                    SELECT %s, 0, 1, 0
                    FROM likes l JOIN posts p ON p.id = l.post_id WHERE p.author_id = ?
                  UNION ALL
                    SELECT %s, 0, 0, 1
                    FROM comments c JOIN posts p ON p.id = c.post_id WHERE p.author_id = ?
                ) GROUP BY week ORDER BY week
                """.formatted(week("p.created_at"), week("l.created_at"), week("c.created_at"));
        return jdbc.query(sql,
                (rs, i) -> new AnalyticsResponse.WeeklyStat(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                userId, userId, userId);
    }

    /** 2. Top 5 postari dupa engagement. */
    List<AnalyticsResponse.TopPost> topPosts(String userId) {
        String sql = """
                SELECT p.id, p.content, p.image_url, %s AS media,
                       (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS likes,
                       (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comments
                FROM posts p
                WHERE p.author_id = ?
                ORDER BY (likes + comments) DESC, p.created_at DESC
                LIMIT 5
                """.formatted(MEDIA_TYPE);
        return jdbc.query(sql, (rs, i) -> {
            long likes = rs.getLong("likes");
            long comments = rs.getLong("comments");
            String imageUrl = rs.getString("image_url");
            String media = imageUrl == null || imageUrl.isBlank() ? null : rs.getString("media");
            return new AnalyticsResponse.TopPost(rs.getString("id"), rs.getString("content"), imageUrl, media,
                    likes, comments, likes + comments);
        }, userId);
    }

    /** 3. Raportul like-uri / comentarii pentru toate postarile. */
    AnalyticsResponse.LikeCommentRatio likeCommentRatio(String userId) {
        String sql = """
                SELECT (SELECT COUNT(*) FROM likes l JOIN posts p ON p.id = l.post_id WHERE p.author_id = ?),
                       (SELECT COUNT(*) FROM comments c JOIN posts p ON p.id = c.post_id WHERE p.author_id = ?)
                """;
        return jdbc.queryForObject(sql, (rs, i) -> {
            long likes = rs.getLong(1);
            long comments = rs.getLong(2);
            return new AnalyticsResponse.LikeCommentRatio(likes, comments,
                    comments == 0 ? null : (double) likes / comments);
        }, userId, userId);
    }

    /** 4. Media like-uri / postare, pe tip media (poze vs. videoclipuri). Postarile fara media sunt ignorate. */
    List<AnalyticsResponse.MediaStat> averageLikesByMedia(String userId) {
        String sql = """
                SELECT media, COUNT(*), AVG(likes) FROM (
                    SELECT %s AS media,
                           (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS likes
                    FROM posts p
                    WHERE p.author_id = ? AND p.image_url IS NOT NULL AND p.image_url <> ''
                ) GROUP BY media ORDER BY media
                """.formatted(MEDIA_TYPE);
        return jdbc.query(sql,
                (rs, i) -> new AnalyticsResponse.MediaStat(rs.getString(1), rs.getLong(2), rs.getDouble(3)),
                userId);
    }

    /**
     * 5. Locul in clasamentul utilizatorilor dupa engagement mediu / postare.
     * rank = 1 + numarul de utilizatori cu media strict mai mare; topPercent = rank / total * 100.
     */
    AnalyticsResponse.Ranking ranking(String userId) {
        String sql = """
                WITH lc AS (SELECT post_id, COUNT(*) AS n FROM likes GROUP BY post_id),
                     cc AS (SELECT post_id, COUNT(*) AS n FROM comments GROUP BY post_id),
                user_avg AS (
                    SELECT p.author_id, AVG(COALESCE(lc.n, 0) + COALESCE(cc.n, 0)) AS avg_e
                    FROM posts p
                    LEFT JOIN lc ON lc.post_id = p.id
                    LEFT JOIN cc ON cc.post_id = p.id
                    GROUP BY p.author_id)
                SELECT (SELECT avg_e FROM user_avg WHERE author_id = ?),
                       (SELECT COUNT(*) FROM user_avg
                         WHERE avg_e > (SELECT avg_e FROM user_avg WHERE author_id = ?)),
                       (SELECT COUNT(*) FROM user_avg)
                """;
        return jdbc.queryForObject(sql, (rs, i) -> {
            double mine = rs.getDouble(1);
            if (rs.wasNull()) {
                return null; // utilizatorul nu are postari
            }
            long better = rs.getLong(2);
            long total = rs.getLong(3);
            long rank = better + 1;
            double topPercent = Math.round((double) rank / total * 1000.0) / 10.0;
            return new AnalyticsResponse.Ranking(Math.round(mine * 100.0) / 100.0, rank, total, topPercent);
        }, userId, userId);
    }
}
