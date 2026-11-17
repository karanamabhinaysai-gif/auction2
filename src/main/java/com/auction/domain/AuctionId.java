package com.auction.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * U1-2: Strongly typed immutable Identifier for an Auction.
 */
public record AuctionId(String value) {
    public AuctionId {
        Objects.requireNonNull(value, "AuctionId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AuctionId cannot be blank");
        }
    }

    public static AuctionId generate() {
        return new AuctionId(UUID.randomUUID().toString());
    }

    public static AuctionId of(String value) {
        return new AuctionId(value);
    }
}
