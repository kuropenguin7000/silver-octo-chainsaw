package io.kessai.wallet.wallet;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
import io.kessai.wallet.ledger.Account;
import io.kessai.wallet.ledger.AccountRepository;
import io.kessai.wallet.ledger.AccountType;
import io.kessai.wallet.ledger.LedgerPosting;
import io.kessai.wallet.ledger.LedgerService;
import io.kessai.wallet.shared.error.DomainException;
import io.kessai.wallet.shared.error.ErrorCode;
import io.kessai.wallet.user.UserService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final UserService userService;
    private final LedgerService ledgerService;

    WalletService(WalletRepository walletRepository,
                  AccountRepository accountRepository,
                  UserService userService,
                  LedgerService ledgerService) {
        this.walletRepository = walletRepository;
        this.accountRepository = accountRepository;
        this.userService = userService;
        this.ledgerService = ledgerService;
    }

    /**
     * Opens a wallet and its balance account as one unit. A wallet without its account is a
     * broken state that must never be observable, so both inserts share this transaction.
     *
     * <p>Depends on {@link UserService} rather than {@code UserRepository} because in Week 11 this
     * call becomes a remote one, and a service is what a Feign client replaces.
     */
    @Transactional
    public WalletWithBalance open(UUID userId, Currency currency) {
        userService.getById(userId);

        if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
            throw alreadyExists(userId, currency);
        }

        Wallet wallet = Wallet.open(userId, currency);
        Account balanceAccount =
                Account.forWallet(wallet.getId(), AccountType.USER_BALANCE, currency);

        try {
            walletRepository.saveAndFlush(wallet);
            accountRepository.saveAndFlush(balanceAccount);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyExists(userId, currency);
        }

        return new WalletWithBalance(wallet, balanceAccount.balance());
    }

    @Transactional(readOnly = true)
    public WalletWithBalance getById(UUID walletId) {
        return loadWithBalance(walletId);
    }

    @Transactional(readOnly = true)
    public WalletStatement entries(UUID walletId, Pageable pageable) {
        WalletWithBalance wallet = loadWithBalance(walletId);
        return new WalletStatement(
                walletId, wallet.balance(), ledgerService.entriesFor(walletId, pageable));
    }

    @Transactional
    public LedgerPosting topUp(UUID walletId, Money amount, String reference) {
        Wallet wallet = findWallet(walletId);

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new DomainException(
                    ErrorCode.WALLET_NOT_ACTIVE, "Wallet " + walletId + " is " + wallet.getStatus());
        }
        if (wallet.getCurrency() != amount.currency()) {
            throw new DomainException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Wallet " + walletId + " holds " + wallet.getCurrency()
                            + " but the request was for " + amount.currency());
        }

        return ledgerService.recordTopUp(walletId, amount, reference);
    }

    /** A private helper, not a call to getById: self-invocation would bypass the proxy. */
    private WalletWithBalance loadWithBalance(UUID walletId) {
        Wallet wallet = findWallet(walletId);

        // open() guarantees every wallet has this account. Its absence is corrupted state, a 500,
        // not a missing resource -- a 404 here would hide a data-integrity bug.
        Account balanceAccount = accountRepository
                .findByWalletIdAndAccountType(walletId, AccountType.USER_BALANCE)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet " + walletId + " has no USER_BALANCE account"));

        return new WalletWithBalance(wallet, balanceAccount.balance());
    }

    private Wallet findWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.WALLET_NOT_FOUND, "No wallet with id " + walletId));
    }

    private DomainException alreadyExists(UUID userId, Currency currency) {
        return new DomainException(
                ErrorCode.WALLET_ALREADY_EXISTS,
                "User " + userId + " already has a " + currency + " wallet");
    }
}
