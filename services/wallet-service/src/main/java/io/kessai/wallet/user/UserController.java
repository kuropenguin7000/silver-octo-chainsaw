package io.kessai.wallet.user;

import io.kessai.wallet.user.dto.CreateUserRequest;
import io.kessai.wallet.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = @Content)
    @ApiResponse(responseCode = "409", description = "USER_EMAIL_TAKEN", content = @Content)
    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.register(request.displayName(), request.email());

        // Relative: an absolute URI would embed localhost:8080, wrong behind the Week 11 gateway.
        URI location = URI.create("/api/v1/users/" + user.getId());

        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @ApiResponse(responseCode = "200", description = "Found")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @GetMapping("/{userId}")
    ResponseEntity<UserResponse> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(userId)));
    }
}
