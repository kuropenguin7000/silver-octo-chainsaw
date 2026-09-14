package io.kessai.wallet.wallet;

import io.kessai.wallet.wallet.dto.CreateWalletRequest;
import io.kessai.wallet.wallet.dto.WalletResponse;
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
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService walletService;

    WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @ApiResponse(responseCode = "409", description = "WALLET_ALREADY_EXISTS", content = @Content)
    @PostMapping("/users/{userId}/wallets")
    ResponseEntity<WalletResponse> open(@PathVariable UUID userId,
                                        @Valid @RequestBody CreateWalletRequest request) {

        WalletWithBalance opened = walletService.open(userId, request.currency());

        URI location = URI.create("/api/v1/wallets/" + opened.wallet().getId());
        return ResponseEntity.created(location).body(WalletResponse.from(opened));
    }

    @ApiResponse(responseCode = "200", description = "Found")
    @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST", content = @Content)
    @ApiResponse(responseCode = "404", description = "WALLET_NOT_FOUND", content = @Content)
    @GetMapping("/wallets/{walletId}")
    ResponseEntity<WalletResponse> get(@PathVariable UUID walletId) {
        return ResponseEntity.ok(WalletResponse.from(walletService.getById(walletId)));
    }
}
