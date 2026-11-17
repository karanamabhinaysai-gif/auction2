package com.auction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * U1-2: Immutable Bid Value Object.
 * Once created, a bid cannot be altered.
 */
public record Bid(
    String bidId,
    AuctionId auctionId,
    String bidderId,
    Money amount,
    Instant timestamp
) implements Comparable<Bid> {

    public Bid {
        Objects.requireNonNull(bidId, "Bid ID cannot be null");
        Objects.requireNonNull(auctionId, "Auction ID cannot be null");
        Objects.requireNonNull(bidderId, "Bidder ID cannot be null");
        Objects.requireNonNull(amount, "Bid amount cannot be null");
        Objects.requireNonNull(timestamp, "Bid timestamp cannot be null");

        if (bidderId.isBlank()) {
            throw new IllegalArgumentException("Bidder ID cannot be blank");
        }
    }

    public static Bid of(AuctionId auctionId, String bidderId, Money amount) {
        return new Bid(UUID.randomUUID().toString(), auctionId, bidderId, amount, Instant.now());
    }

    @Override
    public int compareTo(Bid other) {
        Objects.requireNonNull(other, "Cannot compare to null Bid");
        return this.amount.compareTo(other.amount);
    }
}
