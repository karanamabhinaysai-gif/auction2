package com.auction.domain;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main Auction Aggregate Root.
 * Demonstrates:
 * - U1-2: Immutable Bid, Money, AuctionId, Increment invariants
 * - U3: Design by Contract (Preconditions, Postconditions, Invariants) on placeBid; sealed AuctionType
 * - U4: State Pattern, Observer Pattern, Strategy Pattern
 * - U5: Compensation workflow (Second-highest bidder offer on payment default)
 * - U6: Atomic Compare-And-Set (CAS) for concurrent bids
 */
public class Auction implements AuctionContext {

    private final AuctionId auctionId;
    private final String sellerId;
    private final AuctionType auctionType;
    private final Money reservePrice;
    private final Money minimumIncrement;
    private final Instant startTime;
    private final Instant endTime;

    // U4: State Pattern
    private AuctionState state;

    // U6: Atomic CAS on highest bid for thread-safe concurrent bidding
    private final AtomicReference<Bid> highestBid = new AtomicReference<>(null);

    // Bidding history (immutable list representation)
    private final List<Bid> bidHistory = new CopyOnWriteArrayList<>();

    // U4: Observer list
    private final List<BidObserver> observers = new CopyOnWriteArrayList<>();

    public Auction(
        AuctionId auctionId,
        String sellerId,
        AuctionType auctionType,
        Money reservePrice,
        Money minimumIncrement,
        Instant startTime,
        Instant endTime
    ) {
        // Contract on construction: Non-null and logical time checks
        this.auctionId = Objects.requireNonNull(auctionId, "Auction ID cannot be null");
        this.sellerId = Objects.requireNonNull(sellerId, "Seller ID cannot be null");
        this.auctionType = Objects.requireNonNull(auctionType, "Auction type cannot be null");
        this.reservePrice = Objects.requireNonNull(reservePrice, "Reserve price cannot be null");
        this.minimumIncrement = Objects.requireNonNull(minimumIncrement, "Minimum increment cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "Start time cannot be null");
        this.endTime = Objects.requireNonNull(endTime, "End time cannot be null");

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        this.state = new DraftState();
        checkClassInvariants();
    }

    public void addObserver(BidObserver observer) {
        observers.add(Objects.requireNonNull(observer));
    }

    public void removeObserver(BidObserver observer) {
        observers.remove(observer);
    }

    // Lifecycle transitions
    public void startAuction() {
        state.start(this);
    }

    public void endAuction() {
        state.end(this);
    }

    public void cancelAuction() {
        state.cancel(this);
    }

    // =========================================================================
    // U3: CONTRACTS ON placeBid (Design by Contract)
    // =========================================================================
    /**
     * Places a bid adhering strictly to DbC:
     * - PRECONDITIONS:
     *   1. Non-null Bid and Bid matching this AuctionId
     *   2. Seller cannot bid on their own auction
     *   3. Auction state must be ACTIVE
     *   4. AuctionType-specific preconditions (English vs Sealed)
     * - POSTCONDITIONS:
     *   1. New bid is safely stored in bid history
     *   2. In English auction: highestBid reference points to newBid
     *   3. Previous highest bidder receives outbid notification
     * - CLASS INVARIANTS:
     *   Highest bid monotonically increases and holds proper monetary scale.
     */
    public boolean placeBid(Bid newBid) {
        Instant now = Instant.now();

        // -------------------------------------------------------------
        // 1. PRECONDITIONS (DbC)
        // -------------------------------------------------------------
        if (newBid == null) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Bid cannot be null"
            );
        }

        if (!newBid.auctionId().equals(this.auctionId)) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Bid auctionId (" + newBid.auctionId().value() + ") does not match (" + this.auctionId.value() + ")"
            );
        }

        if (newBid.bidderId().equalsIgnoreCase(this.sellerId)) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.PRECONDITION,
                "Seller cannot bid on their own auction (bidder: " + newBid.bidderId() + ")"
            );
        }

        // Delegate to state (State pattern ensures bids are only accepted when ACTIVE)
        // Delegate to sealed AuctionType for rules (English increment vs Sealed blind)
        state.placeBid(this, newBid);

        // -------------------------------------------------------------
        // 2. CLASS INVARIANTS CHECK (DbC)
        // -------------------------------------------------------------
        checkClassInvariants();

        return true;
    }

    /**
     * Internal atomic execution called by ActiveState.
     * U6: Implements CAS (Compare-And-Set) loop for last-second concurrent bids.
     */
    @Override
    public void acceptBidInternal(Bid newBid) {
        Instant now = Instant.now();
        Bid current;

        // U6: Compare-and-Set loop to safely handle concurrent last-second bids
        do {
            current = highestBid.get();

            // U3: Precondition check against current highest bid
            auctionType.validateBidPreconditions(
                newBid,
                current,
                reservePrice,
                minimumIncrement,
                now,
                endTime
            );

        } while (!highestBid.compareAndSet(current, newBid));

        // Bid successfully placed atomically
        bidHistory.add(newBid);

        // -------------------------------------------------------------
        // 3. POSTCONDITIONS & OBSERVER NOTIFICATIONS (DbC & U4)
        // -------------------------------------------------------------
        if (auctionType.areBidsPubliclyVisible()) {
            if (highestBid.get() != newBid) {
                throw new ContractViolationException(
                    ContractViolationException.ViolationType.POSTCONDITION,
                    "Highest bid must be the newly placed bid"
                );
            }

            // Notify outbid user
            if (current != null) {
                for (BidObserver observer : observers) {
                    observer.onOutbid(auctionId, current.bidderId(), newBid);
                }
            }

            // Broadcast new highest bid
            for (BidObserver observer : observers) {
                observer.onNewHighestBid(auctionId, newBid);
            }
        }
    }

    // =========================================================================
    // U5: WINNER SETTLEMENT & COMPENSATION WORKFLOW
    // =========================================================================
    /**
     * Settle auction with the winner. If winner payment fails, compensates by
     * offering the item to the second-highest bidder.
     */
    public SettlementResult settleWinner(PaymentGateway paymentGateway) {
        if (!"ENDED".equals(state.getName())) {
            throw new IllegalStateException("Auction must be ended to settle.");
        }

        Bid winningBid = highestBid.get();
        if (winningBid == null) {
            return new SettlementResult(false, null, "No bids were placed on this auction.");
        }

        System.out.printf("[SETTLEMENT] Attempting payment of %s for highest bidder: %s...%n",
            winningBid.amount(), winningBid.bidderId());

        boolean paymentSuccess = paymentGateway.processPayment(winningBid.bidderId(), winningBid.amount());

        if (paymentSuccess) {
            System.out.printf("[SETTLEMENT] Payment SUCCESSFUL for winner %s! Auction settled.%n", winningBid.bidderId());
            return new SettlementResult(true, winningBid, "Settled with primary winner: " + winningBid.bidderId());
        }

        // U5: COMPENSATION WORKFLOW - Payment Failed!
        System.out.printf("[SETTLEMENT - FAILURE] Winner %s payment FAILED! Triggering compensation workflow...%n",
            winningBid.bidderId());

        Optional<Bid> secondHighestBid = getSecondHighestBid();
        if (secondHighestBid.isPresent()) {
            Bid runnerUp = secondHighestBid.get();
            System.out.printf("[COMPENSATION] Second-chance offer extended to second-highest bidder: %s at their bid price of %s.%n",
                runnerUp.bidderId(), runnerUp.amount());

            boolean runnerUpPayment = paymentGateway.processPayment(runnerUp.bidderId(), runnerUp.amount());
            if (runnerUpPayment) {
                System.out.printf("[COMPENSATION SUCCESS] Runner-up %s accepted and paid! Item awarded via compensation.%n",
                    runnerUp.bidderId());
                return new SettlementResult(true, runnerUp, "Settled with second-highest bidder via compensation: " + runnerUp.bidderId());
            } else {
                return new SettlementResult(false, null, "Second-highest bidder payment also failed. Re-auction required.");
            }
        }

        return new SettlementResult(false, null, "Winner defaulted and no second bidder available. Re-auction required.");
    }

    public Optional<Bid> getSecondHighestBid() {
        if (bidHistory.size() < 2) {
            return Optional.empty();
        }
        // Sort distinct bidders descending by amount
        return bidHistory.stream()
            .sorted(Comparator.comparing(Bid::amount).reversed())
            .skip(1)
            .findFirst();
    }

    // =========================================================================
    // INVARIANT CHECK
    // =========================================================================
    private void checkClassInvariants() {
        if (reservePrice.compareTo(Money.usd(0)) < 0) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.INVARIANT,
                "Reserve price cannot be negative"
            );
        }
        if (minimumIncrement.compareTo(Money.usd(0)) <= 0) {
            throw new ContractViolationException(
                ContractViolationException.ViolationType.INVARIANT,
                "Minimum increment must be strictly positive"
            );
        }
    }

    // Context hooks
    @Override public AuctionId getAuctionId() { return auctionId; }
    @Override public AuctionType getAuctionType() { return auctionType; }
    @Override public Money getReservePrice() { return reservePrice; }
    @Override public Money getMinimumIncrement() { return minimumIncrement; }
    @Override public Instant getEndTime() { return endTime; }
    @Override public Bid getCurrentHighestBid() { return highestBid.get(); }
    @Override public void setInternalState(AuctionState newState) { this.state = newState; }
    @Override public void closeAuctionInternal() { /* Cleanup/Finalize */ }
    @Override public void cancelAuctionInternal() { /* Cleanup/Cancel */ }

    public AuctionState getState() { return state; }
    public String getSellerId() { return sellerId; }
    public List<Bid> getBidHistory() { return Collections.unmodifiableList(bidHistory); }

    public record SettlementResult(boolean settled, Bid winningBid, String details) {}

    public interface PaymentGateway {
        boolean processPayment(String bidderId, Money amount);
    }
}
