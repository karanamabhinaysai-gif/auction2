package com.auction.domain;

import java.util.Optional;

/**
 * U4 Strategy: Incremental Auto-bid.
 * Places the minimum acceptable bid (current highest + increment) as long as it does not exceed maxCeiling.
 */
public class IncrementalAutoBidStrategy implements AutoBidStrategy {

    @Override
    public String getStrategyName() {
        return "Incremental Minimum Counter-Bid";
    }

    @Override
    public Optional<Money> calculateNextBid(
        String autoBidderId,
        Bid currentHighestBid,
        Money reservePrice,
        Money minIncrement,
        Money maxCeiling
    ) {
        // If current highest bidder is already this auto-bidder, no need to outbid themselves
        if (currentHighestBid != null && currentHighestBid.bidderId().equalsIgnoreCase(autoBidderId)) {
            return Optional.empty();
        }

        Money nextBidAmount;
        if (currentHighestBid == null) {
            nextBidAmount = reservePrice;
        } else {
            nextBidAmount = currentHighestBid.amount().add(minIncrement);
        }

        // Check if next bid is within max ceiling limit
        if (nextBidAmount.compareTo(maxCeiling) <= 0) {
            return Optional.of(nextBidAmount);
        }

        return Optional.empty(); // Ceiling exceeded
    }
}
