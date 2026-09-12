package io.kessai.wallet.wallet;

import io.kessai.common.money.Currency;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    boolean existsByUserIdAndCurrency(UUID userId, Currency currency);
}
