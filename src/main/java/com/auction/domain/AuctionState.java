package com.auction.domain;

/**
 * U4: State Pattern for Auction lifecycle.
 */
public interface AuctionState {
    String getName();

    void placeBid(AuctionContext context, Bid bid);

    void start(AuctionContext context);

    void end(AuctionContext context);

    void cancel(AuctionContext context);
}
