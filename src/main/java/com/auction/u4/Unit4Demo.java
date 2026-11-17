package com.auction.u4;

import com.auction.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UNIT 4 FOCUS:
 * 1. State Pattern for the Auction lifecycle
 * 2. Observer Pattern for real-time outbid and state alerts
 * 3. Strategy Pattern for customizable auto-bidding algorithms
 */
public class Unit4Demo {

    // =========================================================================
    // 1. STATE PATTERN
    // =========================================================================
    public interface DetailedAuctionState {
        String getName();
        void placeBid(AuctionManager context, Bid bid);
        void start(AuctionManager context);
        void pause(AuctionManager context);
        void resume(AuctionManager context);
        void end(AuctionManager context);
        void cancel(AuctionManager context);
    }

    public static class DraftState implements DetailedAuctionState {
        @Override public String getName() { return "DRAFT"; }

        @Override
        public void placeBid(AuctionManager context, Bid bid) {
            throw new IllegalStateException("CONTRACT REJECTION: Cannot place bids while auction is in DRAFT state.");
        }
        @Override
        public void start(AuctionManager context) {
            context.changeState(new ActiveState());
        }
        @Override
        public void pause(AuctionManager context) {
            throw new IllegalStateException("Cannot pause an auction that has not started.");
        }
        @Override
        public void resume(AuctionManager context) {
            throw new IllegalStateException("Draft auction cannot be resumed.");
        }
        @Override
        public void end(AuctionManager context) {
            throw new IllegalStateException("Draft auction cannot end without starting.");
        }
        @Override
        public void cancel(AuctionManager context) {
            context.changeState(new CancelledState("Cancelled by seller prior to start."));
        }
    }

    public static class ActiveState implements DetailedAuctionState {
        @Override public String getName() { return "ACTIVE"; }

        @Override
        public void placeBid(AuctionManager context, Bid bid) {
            context.processValidBid(bid);
        }
        @Override
        public void start(AuctionManager context) {
            throw new IllegalStateException("Auction is already running.");
        }
        @Override
        public void pause(AuctionManager context) {
            context.changeState(new PausedState());
        }
        @Override
        public void resume(AuctionManager context) {
            throw new IllegalStateException("Auction is already active.");
        }
        @Override
        public void end(AuctionManager context) {
            context.changeState(new EndedState());
        }
        @Override
        public void cancel(AuctionManager context) {
            context.changeState(new CancelledState("Auction cancelled by admin."));
        }
    }

    public static class PausedState implements DetailedAuctionState {
        @Override public String getName() { return "PAUSED"; }

        @Override
        public void placeBid(AuctionManager context, Bid bid) {
            throw new IllegalStateException("CONTRACT REJECTION: Bidding temporarily suspended (Auction PAUSED).");
        }
        @Override
        public void start(AuctionManager context) {
            throw new IllegalStateException("Paused auction must be resumed, not restarted.");
        }
        @Override
        public void pause(AuctionManager context) {
            throw new IllegalStateException("Already paused.");
        }
        @Override
        public void resume(AuctionManager context) {
            context.changeState(new ActiveState());
        }
        @Override
        public void end(AuctionManager context) {
            context.changeState(new EndedState());
        }
        @Override
        public void cancel(AuctionManager context) {
            context.changeState(new CancelledState("Auction cancelled while paused."));
        }
    }

    public static class EndedState implements DetailedAuctionState {
        @Override public String getName() { return "ENDED"; }

        @Override
        public void placeBid(AuctionManager context, Bid bid) {
            throw new IllegalStateException("CONTRACT REJECTION: Auction has ENDED. No more bids accepted.");
        }
        @Override public void start(AuctionManager context) { throw new IllegalStateException("Cannot start ended auction."); }
        @Override public void pause(AuctionManager context) { throw new IllegalStateException("Cannot pause ended auction."); }
        @Override public void resume(AuctionManager context) { throw new IllegalStateException("Cannot resume ended auction."); }
        @Override public void end(AuctionManager context) { /* Idempotent */ }
        @Override public void cancel(AuctionManager context) { throw new IllegalStateException("Ended auction cannot be cancelled."); }
    }

    public static class CancelledState implements DetailedAuctionState {
        private final String reason;
        public CancelledState(String reason) { this.reason = reason; }

        @Override public String getName() { return "CANCELLED (" + reason + ")"; }
        @Override public void placeBid(AuctionManager context, Bid bid) { throw new IllegalStateException("Auction CANCELLED: " + reason); }
        @Override public void start(AuctionManager context) { throw new IllegalStateException("Cannot start cancelled auction."); }
        @Override public void pause(AuctionManager context) { throw new IllegalStateException("Cannot pause cancelled auction."); }
        @Override public void resume(AuctionManager context) { throw new IllegalStateException("Cannot resume cancelled auction."); }
        @Override public void end(AuctionManager context) { throw new IllegalStateException("Cannot end cancelled auction."); }
        @Override public void cancel(AuctionManager context) { /* Idempotent */ }
    }

    // =========================================================================
    // 2. OBSERVER PATTERN
    // =========================================================================
    public interface AuctionEventObserver {
        void onOutbid(AuctionId auctionId, String outbidBidderId, Bid newHighestBid);
        void onNewHighestBid(AuctionId auctionId, Bid newHighestBid);
        void onStateChange(AuctionId auctionId, DetailedAuctionState oldState, DetailedAuctionState newState);
    }

    // Concrete Observer A: Push/SMS Notification Service
    public static class PushNotificationService implements AuctionEventObserver {
        @Override
        public void onOutbid(AuctionId auctionId, String outbidBidderId, Bid newHighestBid) {
            System.out.printf("  [PUSH NOTIFICATION] -> Sent to %s: 'You have been OUTBID on %s! New highest bid: %s by %s'%n",
                outbidBidderId, auctionId.value(), newHighestBid.amount(), newHighestBid.bidderId());
        }
        @Override
        public void onNewHighestBid(AuctionId auctionId, Bid newHighestBid) {
            // Can be used for broadcast push
        }
        @Override
        public void onStateChange(AuctionId auctionId, DetailedAuctionState oldState, DetailedAuctionState newState) {
            System.out.printf("  [PUSH BROADCAST] -> Auction %s transitioned: %s ===> %s%n",
                auctionId.value(), oldState.getName(), newState.getName());
        }
    }

    // Concrete Observer B: Audit Log System
    public static class AuditLogObserver implements AuctionEventObserver {
        @Override
        public void onOutbid(AuctionId auctionId, String outbidBidderId, Bid newHighestBid) {}
        @Override
        public void onNewHighestBid(AuctionId auctionId, Bid newHighestBid) {
            System.out.printf("  [AUDIT LOG] -> Auction %s: Bid %s accepted from %s at %s%n",
                auctionId.value(), newHighestBid.amount(), newHighestBid.bidderId(), newHighestBid.timestamp());
        }
        @Override
        public void onStateChange(AuctionId auctionId, DetailedAuctionState oldState, DetailedAuctionState newState) {
            System.out.printf("  [AUDIT LOG] -> State change recorded: %s to %s%n", oldState.getName(), newState.getName());
        }
    }

    // =========================================================================
    // 3. STRATEGY PATTERN FOR AUTO-BIDDING
    // =========================================================================
    public interface AutoBiddingStrategy {
        String getStrategyName();
        Optional<Money> determineCounterBid(
            String botId,
            Bid currentHighestBid,
            Money reservePrice,
            Money minIncrement,
            Money maxBudget
        );
    }

    /**
     * Strategy 1: Incremental Minimum Strategy.
     * Bids the exact minimum increment above current highest.
     */
    public static class IncrementalMinimumStrategy implements AutoBiddingStrategy {
        @Override public String getStrategyName() { return "Conservative Incremental (H + MinIncrement)"; }

        @Override
        public Optional<Money> determineCounterBid(String botId, Bid currentHighestBid, Money reservePrice, Money minIncrement, Money maxBudget) {
            if (currentHighestBid != null && currentHighestBid.bidderId().equalsIgnoreCase(botId)) {
                return Optional.empty(); // Already winning
            }
            Money target = (currentHighestBid == null) ? reservePrice : currentHighestBid.amount().add(minIncrement);
            return (target.compareTo(maxBudget) <= 0) ? Optional.of(target) : Optional.empty();
        }
    }

    /**
     * Strategy 2: Aggressive Jump Strategy.
     * Jumps the bid by multiple increments to intimidate competitors.
     */
    public static class AggressiveJumpStrategy implements AutoBiddingStrategy {
        private final int jumpMultiplier;

        public AggressiveJumpStrategy(int jumpMultiplier) {
            this.jumpMultiplier = jumpMultiplier;
        }

        @Override public String getStrategyName() { return "Aggressive Intimidation (" + jumpMultiplier + "x Increment)"; }

        @Override
        public Optional<Money> determineCounterBid(String botId, Bid currentHighestBid, Money reservePrice, Money minIncrement, Money maxBudget) {
            if (currentHighestBid != null && currentHighestBid.bidderId().equalsIgnoreCase(botId)) {
                return Optional.empty();
            }
            Money jumpIncrement = minIncrement;
            for (int i = 1; i < jumpMultiplier; i++) {
                jumpIncrement = jumpIncrement.add(minIncrement);
            }
            Money target = (currentHighestBid == null) ? reservePrice.add(jumpIncrement) : currentHighestBid.amount().add(jumpIncrement);
            return (target.compareTo(maxBudget) <= 0) ? Optional.of(target) : Optional.empty();
        }
    }

    // =========================================================================
    // SYNERGY: OBSERVER TRIGGERING STRATEGY
    // =========================================================================
    public static class AutoBidAgent implements AuctionEventObserver {
        private final String botId;
        private final Money maxBudget;
        private final AutoBiddingStrategy strategy;
        private final AuctionManager auction;

        public AutoBidAgent(String botId, Money maxBudget, AutoBiddingStrategy strategy, AuctionManager auction) {
            this.botId = botId;
            this.maxBudget = maxBudget;
            this.strategy = strategy;
            this.auction = auction;
        }

        public void triggerInitialBid() {
            Optional<Money> bidAmount = strategy.determineCounterBid(
                botId,
                auction.getCurrentHighestBid(),
                auction.getReservePrice(),
                auction.getMinimumIncrement(),
                maxBudget
            );
            if (bidAmount.isPresent()) {
                System.out.printf("  [AUTO-BID BOT: %s] -> Initializing bid at %s using '%s'...%n",
                    botId, bidAmount.get(), strategy.getStrategyName());
                auction.placeBid(Bid.of(auction.getAuctionId(), botId, bidAmount.get()));
            }
        }

        @Override
        public void onOutbid(AuctionId auctionId, String outbidBidderId, Bid newHighestBid) {
            if (outbidBidderId.equalsIgnoreCase(botId)) {
                System.out.printf("  [AUTO-BID BOT: %s] -> OUTBID ALERT received! Strategy: '%s'.%n",
                    botId, strategy.getStrategyName());

                Optional<Money> nextBidAmount = strategy.determineCounterBid(
                    botId,
                    newHighestBid,
                    auction.getReservePrice(),
                    auction.getMinimumIncrement(),
                    maxBudget
                );

                if (nextBidAmount.isPresent()) {
                    System.out.printf("  [AUTO-BID BOT: %s] -> Counter-bidding with %s (Budget ceiling: %s)...%n",
                        botId, nextBidAmount.get(), maxBudget);
                    auction.placeBid(Bid.of(auctionId, botId, nextBidAmount.get()));
                } else {
                    System.out.printf("  [AUTO-BID BOT: %s] -> Target counter-bid exceeds budget ceiling of %s. Bot bows out.%n",
                        botId, maxBudget);
                }
            }
        }

        @Override public void onNewHighestBid(AuctionId auctionId, Bid newHighestBid) {}
        @Override public void onStateChange(AuctionId auctionId, DetailedAuctionState oldState, DetailedAuctionState newState) {}
    }

    // =========================================================================
    // AUCTION MANAGER CONTEXT
    // =========================================================================
    public static class AuctionManager {
        private final AuctionId auctionId;
        private final Money reservePrice;
        private final Money minimumIncrement;
        private DetailedAuctionState state;
        private Bid currentHighestBid;
        private final List<AuctionEventObserver> observers = new CopyOnWriteArrayList<>();

        public AuctionManager(AuctionId auctionId, Money reservePrice, Money minimumIncrement) {
            this.auctionId = auctionId;
            this.reservePrice = reservePrice;
            this.minimumIncrement = minimumIncrement;
            this.state = new DraftState();
        }

        public void addObserver(AuctionEventObserver observer) { observers.add(observer); }

        public void changeState(DetailedAuctionState newState) {
            DetailedAuctionState oldState = this.state;
            this.state = newState;
            observers.forEach(o -> o.onStateChange(auctionId, oldState, newState));
        }

        public void start() { state.start(this); }
        public void pause() { state.pause(this); }
        public void resume() { state.resume(this); }
        public void end() { state.end(this); }
        public void cancel() { state.cancel(this); }

        public void placeBid(Bid bid) {
            state.placeBid(this, bid);
        }

        void processValidBid(Bid bid) {
            Money minRequired = (currentHighestBid == null) ? reservePrice : currentHighestBid.amount().add(minimumIncrement);
            if (bid.amount().compareTo(minRequired) < 0) {
                throw new IllegalArgumentException("Bid " + bid.amount() + " does not meet required " + minRequired);
            }

            Bid previous = this.currentHighestBid;
            this.currentHighestBid = bid;

            // Broadcast new highest bid
            observers.forEach(o -> o.onNewHighestBid(auctionId, bid));

            // Notify previously outbid bidder
            if (previous != null) {
                observers.forEach(o -> o.onOutbid(auctionId, previous.bidderId(), bid));
            }
        }

        public Money getReservePrice() { return reservePrice; }
        public Money getMinimumIncrement() { return minimumIncrement; }
        public DetailedAuctionState getState() { return state; }
        public Bid getCurrentHighestBid() { return currentHighestBid; }
        public AuctionId getAuctionId() { return auctionId; }
    }

    // =========================================================================
    // MAIN DEMO
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("  ONLINE AUCTION PLATFORM - UNIT 4: STATE, OBSERVER & STRATEGY PATTERNS");
        System.out.println("========================================================================\n");

        AuctionId auctionId = AuctionId.of("AUC-U4-VINTAGE-GUITAR");
        AuctionManager auction = new AuctionManager(auctionId, Money.usd(500.00), Money.usd(50.00));

        // 1. Attach Human Notification Observers
        PushNotificationService pushNotifier = new PushNotificationService();
        AuditLogObserver auditLogger = new AuditLogObserver();
        auction.addObserver(pushNotifier);
        auction.addObserver(auditLogger);

        // ---------------------------------------------------------------------
        // PART 1: STATE PATTERN DEMO
        // ---------------------------------------------------------------------
        System.out.println(">>> 1. STATE PATTERN (LIFECYCLE & TRANSITION GUARDS) <<<");
        System.out.println("Initial State: " + auction.getState().getName());

        // Guard test: Cannot bid in Draft
        try {
            System.out.println("\n[Test Guard] Bidder Alice bids $500.00 while in DRAFT state...");
            auction.placeBid(Bid.of(auctionId, "Alice", Money.usd(500.00)));
        } catch (IllegalStateException e) {
            System.out.println(">> CAUGHT GUARD: " + e.getMessage());
        }

        // Start auction
        System.out.println("\n[Action] Starting auction (DRAFT -> ACTIVE)...");
        auction.start();

        // Valid bid placed
        System.out.println("\n[Action] Alice places initial bid of $500.00...");
        auction.placeBid(Bid.of(auctionId, "Alice", Money.usd(500.00)));

        // Pause auction
        System.out.println("\n[Action] Pausing auction (ACTIVE -> PAUSED for anti-tamper check)...");
        auction.pause();

        // Guard test: Cannot bid while paused
        try {
            System.out.println("\n[Test Guard] Bob tries to bid $550.00 during PAUSE...");
            auction.placeBid(Bid.of(auctionId, "Bob", Money.usd(550.00)));
        } catch (IllegalStateException e) {
            System.out.println(">> CAUGHT GUARD: " + e.getMessage());
        }

        // Resume auction
        System.out.println("\n[Action] Resuming auction (PAUSED -> ACTIVE)...");
        auction.resume();

        // ---------------------------------------------------------------------
        // PART 2 & 3: OBSERVER PATTERN & AUTO-BID STRATEGY PATTERN IN ACTION
        // ---------------------------------------------------------------------
        System.out.println("\n>>> 2 & 3. OBSERVER + STRATEGY IN ACTION (AUTO-BIDDING WAR) <<<");

        // Bot A: Conservative Incremental Strategy (Ceiling $700)
        AutoBidAgent conservativeBot = new AutoBidAgent(
            "ConservativeBot_Charlie",
            Money.usd(700.00),
            new IncrementalMinimumStrategy(),
            auction
        );
        auction.addObserver(conservativeBot);

        // Bot B: Aggressive 2x Jump Strategy (Ceiling $850)
        AutoBidAgent aggressiveBot = new AutoBidAgent(
            "AggressiveBot_Diana",
            Money.usd(850.00),
            new AggressiveJumpStrategy(2), // Jumps by 2x minimum increment = $100 jump
            auction
        );
        auction.addObserver(aggressiveBot);

        // ConservativeBot starts its auto-bidding
        System.out.println("\n[Action] ConservativeBot joins and outbids Alice...");
        conservativeBot.triggerInitialBid();

        // AggressiveBot counters
        System.out.println("\n[Action] AggressiveBot jumps in to challenge ConservativeBot...");
        aggressiveBot.triggerInitialBid();

        // Human Bidder Bob tries to compete with a high bid of $900.00
        System.out.println("\n[Action] Human Bidder 'Bob' places $900.00, outbidding AggressiveBot...");
        auction.placeBid(Bid.of(auctionId, "Bob", Money.usd(900.00)));

        // ---------------------------------------------------------------------
        // ENDING THE AUCTION
        // ---------------------------------------------------------------------
        System.out.println("\n>>> 4. CLOSING AUCTION & FINAL STATE GUARD <<<");
        auction.end();

        // Verify Ended state guard
        try {
            System.out.println("\n[Test Guard] Late bid from Eve ($1200.00) after auction ENDED...");
            auction.placeBid(Bid.of(auctionId, "Eve", Money.usd(1200.00)));
        } catch (IllegalStateException e) {
            System.out.println(">> CAUGHT GUARD: " + e.getMessage());
        }

        System.out.printf("%nFinal Winning Bid: %s won by %s%n",
            auction.getCurrentHighestBid().amount(), auction.getCurrentHighestBid().bidderId());

        System.out.println("\n========================================================================");
        System.out.println("  UNIT 4 DEMO COMPLETED SUCCESSFULLY!");
        System.out.println("========================================================================");
    }
}
