package io.kessai.wallet.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CreateUserIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearUsers() {
        // Not @Transactional rollback: a test that never commits cannot hit a unique constraint.
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("registers a user and persists it")
    void creates_and_persists() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "rahman@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(responseBody).get("id").asString());

        User persisted = userRepository.findById(id).orElseThrow();
        assertThat(persisted.getDisplayName()).isEqualTo("Rahman");
        assertThat(persisted.getEmail()).isEqualTo("rahman@example.com");
        assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("email is stored lower-cased, so case cannot create a duplicate account")
    void normalises_email_case() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "RAHMAN@Example.COM" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("rahman@example.com"));

        assertThat(userRepository.existsByEmail("rahman@example.com")).isTrue();
    }

    @Test
    @DisplayName("an email padded with whitespace is rejected, not silently trimmed")
    void rejects_email_with_surrounding_whitespace() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "  rahman@example.com  " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("a second registration of the same email is rejected with 409")
    void rejects_duplicate_email() throws Exception {
        String body = """
                { "displayName": "Rahman", "email": "rahman@example.com" }
                """;

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_TAKEN"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("differing only in case is still the same email")
    void rejects_duplicate_email_ignoring_case() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "rahman@example.com" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Someone Else", "email": "RAHMAN@EXAMPLE.COM" }
                                """))
                .andExpect(status().isConflict());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid input is rejected before anything is written")
    void rejects_invalid_input_without_persisting() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "", "email": "nope" }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }
}
