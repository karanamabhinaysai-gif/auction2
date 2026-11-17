package com.auction.domain;

public final class ActiveState implements AuctionState {
    @Override
    public String getName() {
        return "ACTIVE";
    }

    @Override
    public void placeBid(AuctionContext context, Bid bid) {
        context.acceptBidInternal(bid);
    }

    @Override
    public void start(AuctionContext context) {
        throw new IllegalStateException("Auction is already active.");
    }

    @Override
    public void end(AuctionContext context) {
        context.closeAuctionInternal();
        context.setInternalState(new EndedState());
    }

    @Override
    public void cancel(AuctionContext context) {
        context.cancelAuctionInternal();
        context.setInternalState(new CancelledState());
    }
}
