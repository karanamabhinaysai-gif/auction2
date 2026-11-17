package com.auction.domain;

public final class CancelledState implements AuctionState {
    @Override
    public String getName() {
        return "CANCELLED";
    }

    @Override
    public void placeBid(AuctionContext context, Bid bid) {
        throw new IllegalStateException("Auction has been cancelled.");
    }

    @Override
    public void start(AuctionContext context) {
        throw new IllegalStateException("Cancelled auction cannot be started.");
    }

    @Override
    public void end(AuctionContext context) {
        throw new IllegalStateException("Cancelled auction cannot be ended normally.");
    }

    @Override
    public void cancel(AuctionContext context) {
        // Idempotent
    }
}
