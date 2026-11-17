package com.auction;

import com.auction.domain.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.*;

public class AuctionPlatformDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("======================================================================");
        System.out.println(" ONLINE AUCTION PLATFORM - UNIT 3 & CORE ARCHITECTURE DEMO");
        System.out.println("======================================================================\n");

        AuctionId auctionId = AuctionId.of("AUC-2026-X1");
        Money reservePrice = Money.usd(100.00);
        Money minIncrement = Money.usd(10.00);
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(1, ChronoUnit.HOURS);

        // --------------------------------------------------------------------
        // 1. DEMO U3 & U4: English Auction Setup with State & Observer
        // --------------------------------------------------------------------
        System.out.println("--- STEP 1: Initializing English Auction (U3: Sealed Type & DbC) ---");
        Auction englishAuction = new Auction(
            auctionId,
            "Seller_Alice",
            new EnglishAuction(),
            reservePrice,
            minIncrement,
            startTime,
            endTime
        );

        // Attach Observer (U4)
        englishAuction.addObserver(new OutbidAlertNotifier());

        System.out.println("Current State: " + englishAuction.getState().getName());

        // Test State Precondition: Cannot bid in DRAFT state
        try {
            System.out.println("\n[Test] Attempting to place bid in DRAFT state...");
            englishAuction.placeBid(Bid.of(auctionId, "Bidder_Bob", Money.usd(100.00)));
        } catch (IllegalStateException e) {
            System.out.println(">> CAUGHT EXPECTED ERROR: " + e.getMessage());
        }

        // Transition State: DRAFT -> ACTIVE
        englishAuction.startAuction();
        System.out.println("\nAuction Started! New State: " + englishAuction.getState().getName());

        // --------------------------------------------------------------------
        // 2. DEMO U3: Contracts on placeBid (Precondition Enforcement)
        // --------------------------------------------------------------------
        System.out.println("\n--- STEP 2: Verifying DbC Preconditions on placeBid ---");

        // Contract Test A: Bid below reserve price
        try {
            System.out.println("\n[Test A] Bidder_Bob places $80 (Reserve is $100)...");
            englishAuction.placeBid(Bid.of(auctionId, "Bidder_Bob", Money.usd(80.00)));
        } catch (ContractViolationException e) {
            System.out.println(">> CONTRACT VIOLATION: " + e.getMessage());
        }

        // Valid First Bid
        System.out.println("\n[Test Valid Bid] Bidder_Bob places valid first bid: $100.00...");
        englishAuction.placeBid(Bid.of(auctionId, "Bidder_Bob", Money.usd(100.00)));

        // Contract Test B: Seller attempting to bid on their own auction
        try {
            System.out.println("\n[Test B] Seller_Alice attempts to bid on own auction...");
            englishAuction.placeBid(Bid.of(auctionId, "Seller_Alice", Money.usd(120.00)));
        } catch (ContractViolationException e) {
            System.out.println(">> CONTRACT VIOLATION: " + e.getMessage());
        }

        // Contract Test C: Bidder attempting to outbid themselves consecutively
        try {
            System.out.println("\n[Test C] Bidder_Bob attempts to bid again immediately ($120.00)...");
            englishAuction.placeBid(Bid.of(auctionId, "Bidder_Bob", Money.usd(120.00)));
        } catch (ContractViolationException e) {
            System.out.println(">> CONTRACT VIOLATION: " + e.getMessage());
        }

        // Contract Test D: Violating minimum increment (Needs $100 + $10 = $110, offers $105)
        try {
            System.out.println("\n[Test D] Bidder_Charlie bids $105.00 (Current is $100, min increment is $10)...");
            englishAuction.placeBid(Bid.of(auctionId, "Bidder_Charlie", Money.usd(105.00)));
        } catch (ContractViolationException e) {
            System.out.println(">> CONTRACT VIOLATION: " + e.getMessage());
        }

        // Valid Higher Bid triggering Outbid Observer Alert
        System.out.println("\n[Test Valid Bid] Bidder_Charlie bids $120.00 (triggers outbid alert for Bob)...");
        englishAuction.placeBid(Bid.of(auctionId, "Bidder_Charlie", Money.usd(120.00)));

        // --------------------------------------------------------------------
        // 3. DEMO U4: Strategy Pattern for Auto-Bid
        // --------------------------------------------------------------------
        System.out.println("\n--- STEP 3: Auto-Bidding Strategy Pattern ---");
        AutoBidStrategy autoBid = new IncrementalAutoBidStrategy();
        String botBidder = "AutoBot_Dave";
        Money botCeiling = Money.usd(150.00);

        Optional<Money> calculatedBid = autoBid.calculateNextBid(
            botBidder,
            englishAuction.getCurrentHighestBid(),
            reservePrice,
            minIncrement,
            botCeiling
        );

        if (calculatedBid.isPresent()) {
            System.out.println("AutoBot calculated next counter-bid: " + calculatedBid.get());
            englishAuction.placeBid(Bid.of(auctionId, botBidder, calculatedBid.get()));
        }

        // --------------------------------------------------------------------
        // 4. DEMO U6: Last-second concurrent bids (CAS atomic handling)
        // --------------------------------------------------------------------
        System.out.println("\n--- STEP 4: Simulating Last-Second Concurrent Bids (U6 CAS) ---");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<String> bidderTask1 = () -> {
            latch.await();
            try {
                englishAuction.placeBid(Bid.of(auctionId, "Concurrent_Rival1", Money.usd(145.00)));
                return "Concurrent_Rival1 succeeded at $145.00";
            } catch (Exception e) {
                return "Concurrent_Rival1 failed: " + e.getMessage();
            }
        };

        Callable<String> bidderTask2 = () -> {
            latch.await();
            try {
                englishAuction.placeBid(Bid.of(auctionId, "Concurrent_Rival2", Money.usd(160.00)));
                return "Concurrent_Rival2 succeeded at $160.00";
            } catch (Exception e) {
                return "Concurrent_Rival2 failed: " + e.getMessage();
            }
        };

        Future<String> f1 = executor.submit(bidderTask1);
        Future<String> f2 = executor.submit(bidderTask2);

        // Fire both threads simultaneously
        latch.countDown();

        System.out.println(f1.get());
        System.out.println(f2.get());
        executor.shutdown();

        // --------------------------------------------------------------------
        // 5. DEMO U5: Settlement & Compensation on Payment Failure
        // --------------------------------------------------------------------
        System.out.println("\n--- STEP 5: Winner Settlement & Compensation (U5) ---");
        englishAuction.endAuction();
        System.out.println("Auction Ended. Current State: " + englishAuction.getState().getName());

        // Simulate a payment gateway where primary winner defaults on payment
        Auction.PaymentGateway mockGateway = (bidderId, amount) -> {
            if ("Concurrent_Rival2".equalsIgnoreCase(bidderId)) {
                // Winner has insufficient funds / card decline
                return false;
            }
            // Runner-up pays successfully
            return true;
        };

        Auction.SettlementResult settlement = englishAuction.settleWinner(mockGateway);
        System.out.println("Settlement Result: " + settlement.details());

        System.out.println("\n======================================================================");
        System.out.println(" DEMO COMPLETED SUCCESSFULLY WITH ZERO ERRORS!");
        System.out.println("======================================================================");
    }
}
