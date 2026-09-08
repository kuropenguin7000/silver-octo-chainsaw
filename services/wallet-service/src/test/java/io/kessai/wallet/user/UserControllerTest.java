package io.kessai.wallet.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.shared.error.DomainException;
import io.kessai.wallet.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("valid requests")
    class Success {

        @Test
        void returns_201_with_location_and_body() throws Exception {
            User created = User.register("Rahman", "rahman@example.com");
            given(userService.register(anyString(), anyString())).willReturn(created);

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "displayName": "Rahman", "email": "rahman@example.com" }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/users/" + created.getId()))
                    .andExpect(jsonPath("$.id").value(created.getId().toString()))
                    .andExpect(jsonPath("$.displayName").value("Rahman"))
                    .andExpect(jsonPath("$.email").value("rahman@example.com"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void never_exposes_internal_fields() throws Exception {
            given(userService.register(anyString(), anyString()))
                    .willReturn(User.register("Rahman", "rahman@example.com"));

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "displayName": "Rahman", "email": "rahman@example.com" }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.version").doesNotExist())
                    .andExpect(jsonPath("$.updatedAt").doesNotExist());
        }
    }

    @Nested
    @DisplayName("validation failures return 400 with the offending field named")
    class Validation {

        @Test
        void rejects_blank_display_name() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "displayName": "  ", "email": "rahman@example.com" }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        }

        @Test
        void rejects_malformed_email() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "displayName": "Rahman", "email": "not-an-email" }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }

        @Test
        void rejects_display_name_longer_than_the_column() throws Exception {
            String tooLong = "x".repeat(101);

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"displayName\": \"" + tooLong + "\", \"email\": \"a@b.com\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        }
    }

    @Nested
    @DisplayName("domain failures are mapped by ErrorCode")
    class DomainFailures {

        @Test
        void duplicate_email_returns_409_with_code() throws Exception {
            willThrow(new DomainException(
                    ErrorCode.USER_EMAIL_TAKEN, "Email rahman@example.com is already registered"))
                    .given(userService).register(any(), any());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "displayName": "Rahman", "email": "rahman@example.com" }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("USER_EMAIL_TAKEN"))
                    .andExpect(jsonPath("$.title").value("Email already registered"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.instance").value("/api/v1/users"))
                    .andExpect(jsonPath("$.type").value("https://kessai.local/problems/user-email-taken"));
        }
    }
}
