package com.auction.domain;

public class OutbidAlertNotifier implements BidObserver {

    @Override
    public void onNewHighestBid(AuctionId auctionId, Bid newBid) {
        System.out.printf("[OBSERVER - BROADCAST] Auction %s: New highest bid placed by %s of %s%n",
            auctionId.value(), newBid.bidderId(), newBid.amount());
    }

    @Override
    public void onOutbid(AuctionId auctionId, String previousBidderId, Bid newHighestBid) {
        System.out.printf("[OBSERVER - ALERT] Hey %s! You have been OUTBID on Auction %s! Current highest bid is now %s by %s.%n",
            previousBidderId, auctionId.value(), newHighestBid.amount(), newHighestBid.bidderId());
    }
}
