package com.auction.domain;

/**
 * U3: Exception thrown when a Design by Contract (DbC) precondition,
 * postcondition, or invariant fails.
 */
public class ContractViolationException extends RuntimeException {
    public enum ViolationType {
        PRECONDITION,
        POSTCONDITION,
        INVARIANT
    }

    private final ViolationType type;

    public ContractViolationException(ViolationType type, String message) {
        super(type + " VIOLATION: " + message);
        this.type = type;
    }

    public ViolationType getType() {
        return type;
    }
}
