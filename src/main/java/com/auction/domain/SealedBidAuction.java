package com.auction.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * U3: Sealed-Bid Auction implementation.
 * Preconditions:
 * 1. Current time must be strictly before end time.
 * 2. Bid amount must be >= reserve price.
 * 3. Bids are blinded and submitted independently without public visibility of other bids.
 */
public final class SealedBidAuction implements AuctionType {

    @Override
    public String getTypeName() {
        return "Sealed-Bid (Blind)";
    }

    @Override
    public boolean areBidsPubliclyVisible() {
        return false;
    }

    @Override
    public void validateBidPreconditions(
        Bid newBid,
        Bid currentHighestBid,
        Money reservePrice,
        Money minIncrement,
        Instant now,
        Instant endTime
    ) {
        Objects.requireNonNull(newBid, "New bid cannot be null");
        Objects.requireNonNull(reservePrice, "Reserve price cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");

        // Precondition 1: Auction time window contract
        if (!now.isBefore(endTime)) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Sealed submission window closed at " + endTime
            );
        }

        // Precondition 2: Reserve price threshold
        if (newBid.amount().compareTo(reservePrice) < 0) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Sealed bid " + newBid.amount() + " is below reserve price " + reservePrice
            );
        }
    }
}
