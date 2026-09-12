package io.kessai.wallet.ledger;

public enum AccountType {

    /** A user's spendable balance. */
    USER_BALANCE,

    /** Funds authorised but not yet captured. Used from Week 9. */
    USER_PENDING,

    /** Counterparty for money entering the platform. Its balance is negative by design. */
    EXTERNAL_FUNDING
}
