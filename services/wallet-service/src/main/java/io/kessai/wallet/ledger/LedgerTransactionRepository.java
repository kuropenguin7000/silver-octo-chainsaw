package io.kessai.wallet.ledger;

import java.util.UUID;
import org.springframework.data.repository.Repository;

// Deliberately not JpaRepository: the ledger is append-only, so no update or delete is exposed.
public interface LedgerTransactionRepository extends Repository<LedgerTransaction, UUID> {

    LedgerTransaction save(LedgerTransaction transaction);
}
