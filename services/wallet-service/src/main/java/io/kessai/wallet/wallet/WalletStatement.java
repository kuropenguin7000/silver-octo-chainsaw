package io.kessai.wallet.wallet;

import io.kessai.common.money.Money;
import io.kessai.wallet.ledger.LedgerEntryView;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record WalletStatement(UUID walletId, Money balance, Page<LedgerEntryView> entries) {
}
