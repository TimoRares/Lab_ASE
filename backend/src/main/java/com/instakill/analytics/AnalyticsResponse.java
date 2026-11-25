package com.instakill.analytics;

import java.util.List;

/**
 * Statisticile utilizatorului autentificat (calculate cu SQL, vezi AnalyticsRepository).
 * Nu contine date ale altor utilizatori.
 */
public record AnalyticsResponse(
        List<WeeklyStat> weekly,
        List<TopPost> topPosts,
        LikeCommentRatio likeCommentRatio,
        List<MediaStat> averageLikesByMedia,
        Ranking ranking
) {

    /** Postari create, like-uri primite si comentarii primite intr-o saptamana (luni = inceputul saptamanii). */
    public record WeeklyStat(String weekStart, long posts, long likes, long comments) {
    }

    public record TopPost(String id, String content, String imageUrl, String mediaType,
                          long likes, long comments, long engagement) {
    }

    /** {@code ratio} este null cand nu exista niciun comentariu. */
    public record LikeCommentRatio(long likes, long comments, Double ratio) {
    }

    /** {@code mediaType} este "photo" sau "video". */
    public record MediaStat(String mediaType, long posts, double averageLikes) {
    }

    /** Locul utilizatorului dupa engagement mediu / postare; null daca nu are postari. */
    public record Ranking(double averageEngagement, long rank, long totalUsers, double topPercent) {
    }
}
