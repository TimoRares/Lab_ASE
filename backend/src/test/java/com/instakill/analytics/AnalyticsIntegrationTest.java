package com.instakill.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password\"}";
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private String createPost(Cookie author, String content, String imageUrl) throws Exception {
        String image = imageUrl == null ? "null" : "\"" + imageUrl + "\"";
        String json = mockMvc.perform(post("/api/posts").cookie(author).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"imageUrl\":" + image + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void computesStatisticsForCurrentUserOnly() throws Exception {
        Cookie owner = register("analyticsowner");
        Cookie fan = register("analyticsfan");

        String videoPost = createPost(owner, "video post", "/media/a.mp4");
        String photoPost = createPost(owner, "photo post", "/media/b.jpg");
        createPost(fan, "someone else's post", "/media/c.png");   // nu trebuie sa apara in statisticile owner-ului

        // videoPost: 2 like-uri + 1 comentariu; photoPost: 0
        mockMvc.perform(post("/api/posts/" + videoPost + "/likes/toggle").cookie(owner)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + videoPost + "/likes/toggle").cookie(fan)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + videoPost + "/comments").cookie(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"nice\"}"))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(get("/api/analytics/me").cookie(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        // 1. pe saptamana (toate evenimentele sunt create acum => o singura saptamana)
        assertThat(root.get("weekly")).hasSize(1);
        JsonNode week = root.get("weekly").get(0);
        assertThat(week.get("posts").asLong()).isEqualTo(2);
        assertThat(week.get("likes").asLong()).isEqualTo(2);
        assertThat(week.get("comments").asLong()).isEqualTo(1);

        // 2. top postari
        JsonNode top = root.get("topPosts");
        assertThat(top).hasSize(2);
        assertThat(top.get(0).get("id").asText()).isEqualTo(videoPost);
        assertThat(top.get(0).get("engagement").asLong()).isEqualTo(3);
        assertThat(top.get(0).get("mediaType").asText()).isEqualTo("video");
        assertThat(top.get(1).get("id").asText()).isEqualTo(photoPost);

        // 3. raport like-uri / comentarii
        assertThat(root.get("likeCommentRatio").get("ratio").asDouble()).isEqualTo(2.0);

        // 4. media like-uri pe tip media
        JsonNode media = root.get("averageLikesByMedia");
        assertThat(media).hasSize(2);
        for (JsonNode m : media) {
            double expected = m.get("mediaType").asText().equals("video") ? 2.0 : 0.0;
            assertThat(m.get("averageLikes").asDouble()).isEqualTo(expected);
        }

        // 5. clasament
        JsonNode ranking = root.get("ranking");
        assertThat(ranking.get("rank").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(ranking.get("totalUsers").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(ranking.get("topPercent").asDouble()).isBetween(0.0, 100.0);
    }

    @Test
    void userWithoutPostsGetsEmptyStatistics() throws Exception {
        Cookie lonely = register("analyticslonely");

        JsonNode root = objectMapper.readTree(mockMvc.perform(get("/api/analytics/me").cookie(lonely))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(root.get("weekly")).isEmpty();
        assertThat(root.get("topPosts")).isEmpty();
        assertThat(root.get("likeCommentRatio").get("ratio").isNull()).isTrue();
        assertThat(root.get("ranking").isNull()).isTrue();
    }
}
