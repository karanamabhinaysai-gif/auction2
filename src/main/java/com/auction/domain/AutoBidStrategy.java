package com.auction.domain;

import java.util.Optional;

/**
 * U4: Strategy Pattern for automated bidding agents.
 */
public interface AutoBidStrategy {
    String getStrategyName();

    /**
     * Calculates the next bid amount if this bidder needs to counter the current highest bid.
     * Returns Optional.empty() if the max limit is reached or bidding is not warranted.
     */
    Optional<Money> calculateNextBid(
        String autoBidderId,
        Bid currentHighestBid,
        Money reservePrice,
        Money minIncrement,
        Money maxCeiling
    );
}
