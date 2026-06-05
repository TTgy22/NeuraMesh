package com.neuramesh.vm.state;

import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.exception.VMException;

/**
 * 账户状态：地址、余额、nonce。可变对象，配合 {@link GlobalState} 的快照/回滚使用。
 */
public final class AccountState {

    private final byte[] address;
    private long balance;
    private long nonce;

    public AccountState(byte[] address, long balance, long nonce) {
        if (address == null || address.length != CryptoUtils.ADDRESS_LENGTH) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD,
                    "账户地址长度需为 " + CryptoUtils.ADDRESS_LENGTH);
        }
        if (balance < 0) {
            throw new VMException(VMException.Kind.INSUFFICIENT_BALANCE, "余额不可为负");
        }
        this.address = address.clone();
        this.balance = balance;
        this.nonce = nonce;
    }

    /** 深拷贝（用于快照）。 */
    public AccountState copy() {
        return new AccountState(address, balance, nonce);
    }

    public byte[] getAddress() {
        return address.clone();
    }

    public String getAddressHex() {
        return CryptoUtils.toHex(address);
    }

    public long getBalance() {
        return balance;
    }

    public long getNonce() {
        return nonce;
    }

    public void credit(long amount) {
        if (amount < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "credit 金额不可为负");
        }
        this.balance += amount;
    }

    public void debit(long amount) {
        if (amount < 0) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "debit 金额不可为负");
        }
        if (balance < amount) {
            throw new VMException(VMException.Kind.INSUFFICIENT_BALANCE,
                    "余额不足: " + balance + " < " + amount);
        }
        this.balance -= amount;
    }

    public void incrementNonce() {
        this.nonce++;
    }
}
