package com.instakill.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie registerAndGetToken(String username) throws Exception {
        String payload = objectMapper.writeValueAsString(
                new AuthPayload(username, username + "@example.com", "password"));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        Cookie token = result.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/media").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadedFileIsSavedAndServedBack() throws Exception {
        Cookie token = registerAndGetToken("mediauser");
        byte[] bytes = "fake-mp4-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "clip.MP4", "video/mp4", bytes);

        MvcResult upload = mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaUrl").value(org.hamcrest.Matchers.matchesPattern("/media/[0-9a-f-]{36}\\.mp4")))
                .andReturn();

        JsonNode json = objectMapper.readTree(upload.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();

        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    void rejectsEmptyFileAndDisallowedExtension() throws Exception {
        Cookie token = registerAndGetToken("mediauser2");

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .cookie(token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[]{1}))
                        .cookie(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadedImageIsResizedTo300pxWidth() throws Exception {
        Cookie token = registerAndGetToken("mediauser3");
        BufferedImage original = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(original, "png", png);

        MvcResult upload = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png.toByteArray()))
                        .cookie(token))
                .andExpect(status().isCreated())
                .andReturn();
        String mediaUrl = objectMapper.readTree(upload.getResponse().getContentAsString()).get("mediaUrl").asText();

        byte[] served = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage processed = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(processed.getWidth()).isEqualTo(300);
        assertThat(processed.getHeight()).isEqualTo(200);
    }

    @Test
    void rejectsFileWithImageExtensionButInvalidContent() throws Exception {
        Cookie token = registerAndGetToken("mediauser4");

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3}))
                        .cookie(token))
                .andExpect(status().isBadRequest());
    }

    private record AuthPayload(String username, String email, String password) {
    }
}
