package io.kessai.wallet.ledger;

import io.kessai.common.money.Money;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;

    LedgerService(AccountRepository accountRepository,
                  LedgerTransactionRepository transactionRepository,
                  JournalEntryRepository journalEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    /**
     * Money entering the platform: debit {@code EXTERNAL_FUNDING_<currency>}, credit the wallet.
     *
     * <p>Neither idempotent nor safe under concurrency, on purpose. Week 3 adds idempotency keys;
     * Week 2 reproduces the concurrent failure before fixing it.
     */
    @Transactional
    public LedgerPosting recordTopUp(UUID walletId, Money amount, String reference) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Top-up amount must be positive, was " + amount);
        }

        Account userBalance = requireUserBalance(walletId);
        Account funding = accountRepository
                .findBySystemKey("EXTERNAL_FUNDING_" + amount.currency())
                .orElseThrow(() -> new IllegalStateException(
                        "No EXTERNAL_FUNDING account seeded for " + amount.currency()));

        LedgerTransaction transaction = transactionRepository.save(
                LedgerTransaction.of(TransactionType.TOP_UP, amount.currency(), reference));

        post(transaction, funding, amount.negated());
        post(transaction, userBalance, amount);

        return new LedgerPosting(transaction.getId(), TransactionType.TOP_UP,
                amount, userBalance.balance(), transaction.getCreatedAt());
    }

    /** The wallet's own entries only. The matching system-account entries are not the user's business. */
    @Transactional(readOnly = true)
    public Page<LedgerEntryView> entriesFor(UUID walletId, Pageable pageable) {
        return journalEntryRepository.findEntries(requireUserBalance(walletId).getId(), pageable);
    }

    /** The only path that changes a balance, so a balance can never move without its entry. */
    private void post(LedgerTransaction transaction, Account account, Money amount) {
        journalEntryRepository.save(JournalEntry.of(transaction.getId(), account.getId(), amount));
        account.apply(amount);
    }

    private Account requireUserBalance(UUID walletId) {
        return accountRepository
                .findByWalletIdAndAccountType(walletId, AccountType.USER_BALANCE)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet " + walletId + " has no USER_BALANCE account"));
    }
}
