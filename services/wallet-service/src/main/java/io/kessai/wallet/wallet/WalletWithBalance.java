package io.kessai.wallet.wallet;

import io.kessai.common.money.Money;

/**
 * A wallet together with the balance of its {@code USER_BALANCE} account. The two live in
 * different aggregates, so the service returns them as a pair rather than hanging one off the
 * other.
 */
public record WalletWithBalance(Wallet wallet, Money balance) {
}
