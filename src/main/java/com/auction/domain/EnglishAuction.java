package com.auction.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * U3: English Auction implementation.
 * Preconditions:
 * 1. Current time must be strictly before end time.
 * 2. If first bid, amount must be >= reserve price.
 * 3. Subsequent bids must be >= current highest bid + minimum increment.
 * 4. The same bidder cannot outbid themselves consecutively.
 */
public final class EnglishAuction implements AuctionType {

    @Override
    public String getTypeName() {
        return "English (Ascending)";
    }

    @Override
    public boolean areBidsPubliclyVisible() {
        return true;
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
        Objects.requireNonNull(minIncrement, "Minimum increment cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");

        // Precondition 1: Auction time window contract
        if (!now.isBefore(endTime)) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Auction has already concluded at " + endTime + " (current: " + now + ")"
            );
        }

        // Precondition 2: First bid vs subsequent bids
        if (currentHighestBid == null) {
            if (newBid.amount().compareTo(reservePrice) < 0) {
                throw new ContractViolationException(
                    ContractViolationException.ViolationType.PRECONDITION,
                    "First bid " + newBid.amount() + " must meet or exceed reserve price " + reservePrice
                );
            }
        } else {
            // Precondition 3: Bidder cannot outbid themselves
            if (newBid.bidderId().equalsIgnoreCase(currentHighestBid.bidderId())) {
                throw new ContractViolationException(
                    ContractViolationException.ViolationType.PRECONDITION,
                    "Bidder '" + newBid.bidderId() + "' already holds the highest bid"
                );
            }

            // Precondition 4: Bid increment invariant contract
            Money requiredMinimum = currentHighestBid.amount().add(minIncrement);
            if (newBid.amount().compareTo(requiredMinimum) < 0) {
                throw new ContractViolationException(
                    ContractViolationException.ViolationType.PRECONDITION,
                    "Bid amount " + newBid.amount() + " violates increment rule. Must be at least " + requiredMinimum
                );
            }
        }
    }
}
