package com.auction.domain;

/**
 * U4: Observer Pattern for real-time bid and outbid alerts.
 */
public interface BidObserver {
    void onNewHighestBid(AuctionId auctionId, Bid newBid);
    void onOutbid(AuctionId auctionId, String previousBidderId, Bid newHighestBid);
}
