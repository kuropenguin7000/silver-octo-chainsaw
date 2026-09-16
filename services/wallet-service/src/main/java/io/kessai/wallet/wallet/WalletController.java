package io.kessai.wallet.wallet;

import io.kessai.wallet.ledger.LedgerPosting;
import io.kessai.wallet.wallet.dto.CreateWalletRequest;
import io.kessai.wallet.wallet.dto.TopUpRequest;
import io.kessai.wallet.wallet.dto.WalletEntriesResponse;
import io.kessai.wallet.wallet.dto.TopUpResponse;
import io.kessai.wallet.wallet.dto.WalletResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @ApiResponse(responseCode = "200", description = "Found")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = @Content)
    @ApiResponse(responseCode = "404", description = "WALLET_NOT_FOUND", content = @Content)
    @GetMapping("/wallets/{walletId}/entries")
    ResponseEntity<WalletEntriesResponse> entries(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Capped: an unbounded page size lets one request read the whole ledger.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                WalletEntriesResponse.from(walletService.entries(walletId, PageRequest.of(page, size))));
    }

    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = @Content)
    @ApiResponse(responseCode = "404", description = "WALLET_NOT_FOUND", content = @Content)
    @ApiResponse(responseCode = "409", description = "WALLET_NOT_ACTIVE", content = @Content)
    @ApiResponse(responseCode = "422", description = "CURRENCY_MISMATCH", content = @Content)
    @PostMapping("/wallets/{walletId}/topups")
    ResponseEntity<TopUpResponse> topUp(@PathVariable UUID walletId,
                                        @Valid @RequestBody TopUpRequest request) {

        LedgerPosting posting =
                walletService.topUp(walletId, request.amount().toMoney(), request.reference());

        return ResponseEntity.status(HttpStatus.CREATED).body(TopUpResponse.from(walletId, posting));
    }
}
