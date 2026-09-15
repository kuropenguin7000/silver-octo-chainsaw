package io.kessai.wallet.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByWalletIdAndAccountType(UUID walletId, AccountType accountType);

    Optional<Account> findBySystemKey(String systemKey);
}
