package com.auction.domain;

public final class EndedState implements AuctionState {
    @Override
    public String getName() {
        return "ENDED";
    }

    @Override
    public void placeBid(AuctionContext context, Bid bid) {
        throw new IllegalStateException("Auction has ended. No further bids are accepted.");
    }

    @Override
    public void start(AuctionContext context) {
        throw new IllegalStateException("Ended auction cannot be restarted.");
    }

    @Override
    public void end(AuctionContext context) {
        // Idempotent
    }

    @Override
    public void cancel(AuctionContext context) {
        throw new IllegalStateException("Ended auction cannot be cancelled.");
    }
}
