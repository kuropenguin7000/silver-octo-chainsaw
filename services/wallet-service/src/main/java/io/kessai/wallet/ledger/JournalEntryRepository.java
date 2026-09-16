package io.kessai.wallet.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Deliberately not JpaRepository: the ledger is append-only, so no update or delete is exposed.
public interface JournalEntryRepository extends Repository<JournalEntry, UUID> {

    JournalEntry save(JournalEntry entry);

    List<JournalEntry> findByTransactionId(UUID transactionId);

    /**
     * Newest first, with the id as a tiebreak. Entries written by one transaction share a
     * timestamp, and without a deterministic total order rows can repeat or be skipped between
     * pages. UUIDv7 sorts by creation time, so the tiebreak matches the intended order.
     */
    @Query(value = """
            SELECT new io.kessai.wallet.ledger.LedgerEntryView(
                       j.id, j.transactionId, t.type, j.amountMinor, j.currency, j.createdAt)
            FROM JournalEntry j
            JOIN LedgerTransaction t ON t.id = j.transactionId
            WHERE j.accountId = :accountId
            ORDER BY j.createdAt DESC, j.id DESC
            """,
            countQuery = "SELECT COUNT(j) FROM JournalEntry j WHERE j.accountId = :accountId")
    Page<LedgerEntryView> findEntries(@Param("accountId") UUID accountId, Pageable pageable);
}
