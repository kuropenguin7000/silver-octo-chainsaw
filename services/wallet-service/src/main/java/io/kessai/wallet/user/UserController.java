package io.kessai.wallet.user;

import io.kessai.wallet.user.dto.CreateUserRequest;
import io.kessai.wallet.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.register(request.displayName(), request.email());

        // Relative: an absolute URI would embed localhost:8080, wrong behind the Week 11 gateway.
        URI location = URI.create("/api/v1/users/" + user.getId());

        return ResponseEntity.created(location).body(UserResponse.from(user));
    }
}
