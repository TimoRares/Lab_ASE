package com.instakill.notification;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostCreatedNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationSender sender;

    @Test
    void creatingAPostTriggersAsyncNotification() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"notifyuser\",\"email\":\"notify@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie token = register.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(token).isNotNull();

        mockMvc.perform(post("/api/posts")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello notifications\",\"imageUrl\":null}"))
                .andExpect(status().isCreated());

        // listener-ul ruleaza pe alt thread, deci asteptam (max 3s) apelul
        verify(sender, timeout(3000)).send(anyString(), contains("notifyuser"));
    }
}
