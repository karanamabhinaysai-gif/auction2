package com.auction.domain;

import java.time.Instant;

/**
 * Interface used by AuctionState implementations to transition and manipulate auction state.
 */
public interface AuctionContext {
    AuctionId getAuctionId();
    AuctionType getAuctionType();
    Money getReservePrice();
    Money getMinimumIncrement();
    Instant getEndTime();
    Bid getCurrentHighestBid();

    void setInternalState(AuctionState state);
    void acceptBidInternal(Bid bid);
    void closeAuctionInternal();
    void cancelAuctionInternal();
}
