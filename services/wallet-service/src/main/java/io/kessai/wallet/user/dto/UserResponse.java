package io.kessai.wallet.user.dto;

import io.kessai.wallet.user.User;
import io.kessai.wallet.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String displayName,
        String email,
        UserStatus status,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
