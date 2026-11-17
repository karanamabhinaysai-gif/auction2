package com.auction.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * U3: Sealed AuctionType hierarchy (Java 17).
 * Restricts inheritance strictly to permitted subtypes:
 * - EnglishAuction (Open ascending-bid auction)
 * - SealedBidAuction (First-price sealed blind-bid auction)
 */
public sealed interface AuctionType permits EnglishAuction, SealedBidAuction {

    String getTypeName();

    /**
     * U3 DbC Contract: Validates auction-specific pre-conditions before accepting a bid.
     */
    void validateBidPreconditions(
        Bid newBid,
        Bid currentHighestBid,
        Money reservePrice,
        Money minIncrement,
        Instant now,
        Instant endTime
    );

    /**
     * Determines whether bids are publicly visible during the active bidding phase.
     */
    boolean areBidsPubliclyVisible();
}
