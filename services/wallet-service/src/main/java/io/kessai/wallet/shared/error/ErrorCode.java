package io.kessai.wallet.shared.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Constant names are the machine-readable API contract; renaming one is a breaking change. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already registered"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "Wallet already exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private static final String TYPE_PREFIX = "https://kessai.local/problems/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
