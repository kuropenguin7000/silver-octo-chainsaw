package io.kessai.wallet.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GetUserIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("the Location returned by POST is fetchable")
    void follows_location_from_create() throws Exception {
        String location = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "rahman@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rahman"))
                .andExpect(jsonPath("$.email").value("rahman@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("an unknown id is 404, not 500")
    void unknown_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    @DisplayName("an id that is not a UUID is 400 with the same error contract")
    void malformed_id_returns_400() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Parameter 'userId' must be a valid UUID"));
    }
}
