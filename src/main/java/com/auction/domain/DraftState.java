package com.auction.domain;

public final class DraftState implements AuctionState {
    @Override
    public String getName() {
        return "DRAFT";
    }

    @Override
    public void placeBid(AuctionContext context, Bid bid) {
        throw new IllegalStateException("Cannot place bid while auction is still in DRAFT status.");
    }

    @Override
    public void start(AuctionContext context) {
        context.setInternalState(new ActiveState());
    }

    @Override
    public void end(AuctionContext context) {
        throw new IllegalStateException("Draft auction cannot be ended directly. It must be started first or cancelled.");
    }

    @Override
    public void cancel(AuctionContext context) {
        context.setInternalState(new CancelledState());
    }
}
