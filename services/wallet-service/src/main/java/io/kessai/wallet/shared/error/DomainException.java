package io.kessai.wallet.shared.error;

public class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;

    /** The detail is returned to the caller verbatim — keep internals out of it. */
    public DomainException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
