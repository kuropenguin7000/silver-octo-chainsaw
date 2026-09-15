package io.kessai.wallet.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

// Deliberately not JpaRepository: the ledger is append-only, so no update or delete is exposed.
public interface JournalEntryRepository extends Repository<JournalEntry, UUID> {

    JournalEntry save(JournalEntry entry);

    List<JournalEntry> findByTransactionId(UUID transactionId);
}
