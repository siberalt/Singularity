package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.shared.EventSubscriptionBrokerFacade;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.strategy.Strategy;
import com.siberalt.singularity.strategy.market.position.EntryPrice;
import com.siberalt.singularity.strategy.market.position.EntryPriceCalculator;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.volatility.IncrementalATR;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Buys the hourly oversold with a limit under the market, and gives the position a fixed number of
 * hours to bounce.
 * <p>
 * What was measured on 33 instruments over 2021-2026, and what this strategy does:
 * <ul>
 *   <li>The signal is an hour closing with RSI(14) under twenty. It was the one thing that held on
 *       seventeen instruments it was never chosen on; everything tried on top of it - reversal
 *       candles, levels, zones, a rising RSI - either did nothing or made it worse.</li>
 *   <li>The entry is a buy limit {@code limitOffset} hourly ATRs under that close, working for
 *       {@code orderHours} hours and cancelled after. Against buying at the next open, half an ATR
 *       under added 0.09 ATR per signal and a whole ATR 0.14, with fills taken only in minutes that
 *       closed under the limit. In 2021, when the bounces came at once, it lost to the open instead:
 *       a limit misses the signals that turn straight up.</li>
 *   <li>The exit is at the market, {@code holdHours} hours after the signal hour.</li>
 * </ul>
 * One trade at a time: a new signal is not taken while an order works or a position is held.
 * <p>
 * Hours are built here from the minutes the broker sends, bucketed as {@link CandleAggregator} does.
 * An hour is known to be over only when the first minute of a later one arrives, so the signal of a
 * session's last hour is acted on at the next session's first minute - which is also when the
 * limit could first have been placed.
 */
public class RsiLimitEntryStrategy implements Strategy {
    private final EventSubscriptionBrokerFacade broker;
    private final String instrumentId;
    private final String accountId;
    private final ReadCandleRepository candleRepository;
    private final CandleAggregator aggregator = new CandleAggregator();

    private int rsiPeriod = 14;
    private int atrPeriod = 14;
    private double oversold = 20;
    private double limitOffset = 0.5;
    private boolean entryAtMarket;
    private int orderHours = 3;
    private int holdHours = 5;
    private Duration warmup = Duration.ofDays(60);

    private Subscription subscription;
    private boolean initialized;
    private final List<Candle> hour = new ArrayList<>();
    private long hourBucket = Long.MIN_VALUE;

    private IncrementalATR atr;
    private double previousClose = Double.NaN;
    private double averageGain;
    private double averageLoss;
    private int changes;

    private EntryPriceCalculator entryPrices;
    private double exitOffset;
    private double exitRsi;
    private double positionShare = 1;
    private double volumeTimes;
    private int volumeBars = 24;
    private final Deque<Long> volumes = new ArrayDeque<>();

    private String orderId;
    private String exitOrderId;
    private double signalAtr;
    // Hours closed since the signal hour; negative while no trade is on.
    private int hoursSinceSignal = -1;

    public RsiLimitEntryStrategy(
        EventSubscriptionBroker broker,
        String instrumentId,
        String accountId,
        ReadCandleRepository candleRepository
    ) {
        this.broker = EventSubscriptionBrokerFacade.of(broker);
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.candleRepository = candleRepository;
    }

    public RsiLimitEntryStrategy setOversold(double oversold) {
        this.oversold = oversold;
        return this;
    }

    /** How far under the signal hour's close the buy limit goes, in hourly ATRs. */
    public RsiLimitEntryStrategy setLimitOffset(double limitOffset) {
        if (limitOffset < 0) {
            throw new IllegalArgumentException("Лимит не может стоять выше закрытия, получено " + limitOffset);
        }

        this.limitOffset = limitOffset;
        return this;
    }

    /**
     * Buy at the best price on the signal instead of placing a limit - the entry the limit was measured
     * against, kept so the two can be run on the same terms.
     */
    public RsiLimitEntryStrategy setEntryAtMarket(boolean entryAtMarket) {
        this.entryAtMarket = entryAtMarket;
        return this;
    }

    /**
     * Leave the position with a sell limit this many ATRs over what it was bought at, instead of selling
     * at the market the moment the holding time is up. The limit still gives way to a market sale at
     * {@link #setHoldHours}: a target that the price never comes back to is not an exit.
     *
     * @param entryPrices what the position was bought at on average - a limit that is a target has to be
     *                    measured from the fill, not from the signal
     */
    public RsiLimitEntryStrategy setExitLimit(EntryPriceCalculator entryPrices, double exitOffset) {
        if (exitOffset <= 0) {
            throw new IllegalArgumentException("Цель продажи должна быть выше входа, получено " + exitOffset);
        }

        this.entryPrices = Objects.requireNonNull(entryPrices);
        this.exitOffset = exitOffset;
        return this;
    }

    /**
     * Leave as soon as an hour closes with RSI at or over this, instead of waiting out the holding time.
     * {@link #setHoldHours} stays as the cap: an RSI that never recovers must not turn a trade of hours
     * into one of weeks, which would be holding the market rather than the signal.
     * <p>
     * Zero - the default - leaves only the holding time.
     */
    public RsiLimitEntryStrategy setExitRsi(double exitRsi) {
        if (exitRsi < 0 || exitRsi > 100) {
            throw new IllegalArgumentException("RSI выхода должен быть от нуля до ста, получено " + exitRsi);
        }

        this.exitRsi = exitRsi;
        return this;
    }

    /**
     * What share of the money the account still has free one trade may spend. One - the default - is
     * everything, which is what a strategy trading a single instrument does.
     * <p>
     * On one account trading many instruments the signals compete for the same money, and the first one
     * of the day would otherwise leave nothing for the rest. A share of what is free rather than of the
     * whole account keeps that automatic: each new position is smaller than the last, and the money
     * never runs out.
     */
    public RsiLimitEntryStrategy setPositionShare(double positionShare) {
        if (positionShare <= 0 || positionShare > 1) {
            throw new IllegalArgumentException("Доля капитала должна быть в (0, 1], получено " + positionShare);
        }

        this.positionShare = positionShare;
        return this;
    }

    /**
     * Take the signal only when its hour traded at least this many times the median volume of the last
     * {@code bars} hours - the capitulation the oversold is supposed to be.
     * <p>
     * Measured on 33 instruments over four periods: requiring twice the median left RSI under fifteen
     * ahead in all four and lifted the instruments it pays on from 27 to 29 of 33, while dropping a fifth
     * of the signals. Zero - the default - asks nothing of the volume.
     */
    public RsiLimitEntryStrategy setMinVolume(double volumeTimes, int volumeBars) {
        if (volumeTimes < 0) {
            throw new IllegalArgumentException("Объём не может требоваться отрицательным, получено " + volumeTimes);
        }

        if (volumeBars < 1) {
            throw new IllegalArgumentException("Медиану считают хотя бы по одному бару, получено " + volumeBars);
        }

        this.volumeTimes = volumeTimes;
        this.volumeBars = volumeBars;
        return this;
    }

    public RsiLimitEntryStrategy setOrderHours(int orderHours) {
        this.orderHours = orderHours;
        return this;
    }

    public RsiLimitEntryStrategy setHoldHours(int holdHours) {
        if (holdHours < 1) {
            throw new IllegalArgumentException("Держать нужно хотя бы час, получено " + holdHours);
        }

        this.holdHours = holdHours;
        return this;
    }

    /** How much history before the first minute is read to settle RSI and ATR. */
    public RsiLimitEntryStrategy setWarmup(Duration warmup) {
        this.warmup = warmup;
        return this;
    }

    public RsiLimitEntryStrategy setPeriods(int rsiPeriod, int atrPeriod) {
        this.rsiPeriod = rsiPeriod;
        this.atrPeriod = atrPeriod;
        return this;
    }

    @Override
    public void run(Observer observer) {
        atr = new IncrementalATR(atrPeriod);
        subscription = broker.subscribe(new NewCandleSubscriptionSpec(Set.of(instrumentId)), this::handleNewCandle);
    }

    @Override
    public void stop() {
        if (subscription != null && subscription.isActive()) {
            subscription.stop();
        }
    }

    public void handleNewCandle(NewCandleEvent event, Subscription subscription) {
        if (!instrumentId.equals(event.getInstrumentUid())) {
            return;
        }

        Candle minute = event.getCandle();

        if (!initialized) {
            initialized = true;
            warmUp(minute);
        }

        long bucket = aggregator.bucketOf(minute, CandleInterval.HOUR);

        if (bucket != hourBucket) {
            if (!hour.isEmpty()) {
                try {
                    onHourClosed(aggregator.merge(hour));
                } catch (AbstractException e) {
                    throw new RuntimeException(e);
                }
            }

            hour.clear();
            hourBucket = bucket;
        }

        hour.add(minute);
    }

    /**
     * Feeds the indicators the hours before the first minute, without trading on them. The minutes
     * of the hour already under way are kept as its start.
     */
    protected void warmUp(Candle first) {
        List<Candle> history = candleRepository.getPeriod(
            first.instrumentId(), first.getTime().minus(warmup), first.getTime());
        long firstBucket = aggregator.bucketOf(first, CandleInterval.HOUR);
        List<Candle> earlier = new ArrayList<>();

        for (Candle minute : history) {
            if (!minute.getTime().isBefore(first.getTime())) {
                break;
            }

            if (aggregator.bucketOf(minute, CandleInterval.HOUR) == firstBucket) {
                hour.add(minute);
            } else {
                earlier.add(minute);
            }
        }

        for (Candle bar : aggregator.aggregate(earlier, CandleInterval.HOUR)) {
            update(bar);
        }

        hourBucket = firstBucket;
    }

    protected void onHourClosed(Candle bar) throws AbstractException {
        double rsi = update(bar);

        if (hoursSinceSignal >= 0) {
            hoursSinceSignal++;

            if (hoursSinceSignal == orderHours) {
                cancelIfWorking(orderId);
                orderId = null;
            }

            long free = broker.getPositionSize(accountId, instrumentId);
            long owned = broker.getHeldPositionSize(accountId, instrumentId);
            // The bounce is over as soon as the market calls this instrument dear again.
            boolean recovered = exitRsi > 0 && owned > 0 && !Double.isNaN(rsi) && rsi >= exitRsi;

            if (hoursSinceSignal >= holdHours || recovered) {
                cancelIfWorking(orderId);
                cancelIfWorking(exitOrderId);
                orderId = null;
                exitOrderId = null;

                // What the cancelled sell held is free again, so the position is read after it.
                long left = broker.getPositionSize(accountId, instrumentId);

                if (left > 0) {
                    broker.sellBestPrice(accountId, instrumentId, left);
                }

                hoursSinceSignal = -1;
            } else if (owned == 0) {
                if (exitOrderId != null) {
                    // The target was reached and the position is gone: the trade is over early.
                    cancelIfWorking(orderId);
                    orderId = null;
                    exitOrderId = null;
                    hoursSinceSignal = -1;
                } else if (hoursSinceSignal >= orderHours) {
                    // The order ran out without a fill: nothing to hold, so the next signal may come.
                    hoursSinceSignal = -1;
                }
            } else if (exitOrderId == null && entryPrices != null && free > 0) {
                placeExitLimit(free);
            }

            return;
        }

        if (Double.isNaN(rsi) || !atr.ready() || rsi >= oversold || !tradedEnough(bar)) {
            return;
        }

        double limit = bar.getCloseAsDouble() - limitOffset * atr.value();
        long lots = (long) (broker.getMaxBuyQuantity(accountId, instrumentId, OrderType.MARKET) * positionShare);

        if (limit <= 0 || lots <= 0) {
            return;
        }

        orderId = entryAtMarket
            ? broker.buyBestPrice(accountId, instrumentId, lots).getOrderId()
            : broker.buyLimit(accountId, instrumentId, (int) Math.min(Integer.MAX_VALUE, lots), limit).getOrderId();
        signalAtr = atr.value();
        hoursSinceSignal = 0;
    }

    /** The target the position is offered at: what it cost plus the offset, in the signal hour's ATR. */
    protected void placeExitLimit(long lots) throws AbstractException {
        EntryPrice entry = entryPrices.calculate(accountId, instrumentId);

        if (entry.isEmpty()) {
            return;
        }

        exitOrderId = broker.sellLimit(accountId, instrumentId, lots,
            entry.averagePrice().toDouble() + exitOffset * signalAtr).getOrderId();
    }

    protected void cancelIfWorking(String id) throws AbstractException {
        if (id == null) {
            return;
        }

        boolean working = broker.getOrders(accountId).getOrders().stream()
            .anyMatch(order -> id.equals(order.getOrderId()));

        if (working) {
            broker.cancelOrder(accountId, id);
        }
    }

    /**
     * Whether this hour traded enough to be a capitulation rather than a drift. Until there are
     * {@link #setMinVolume} bars to take a median of, nothing is asked - the alternative is to judge by
     * half a window, which is not the same rule.
     */
    protected boolean tradedEnough(Candle bar) {
        if (volumeTimes <= 0 || volumes.size() < volumeBars) {
            return true;
        }

        long[] sorted = volumes.stream().mapToLong(Long::longValue).sorted().toArray();

        return bar.volume() >= volumeTimes * sorted[sorted.length / 2];
    }

    /** Adds a closed hour to ATR and to Wilder's RSI, and returns the RSI, or NaN until it has settled. */
    protected double update(Candle bar) {
        atr.add(bar);
        volumes.addLast(bar.volume());

        if (volumes.size() > volumeBars) {
            volumes.removeFirst();
        }


        double close = bar.getCloseAsDouble();

        if (!Double.isNaN(previousClose)) {
            double change = close - previousClose;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            changes++;

            if (changes <= rsiPeriod) {
                averageGain += gain / rsiPeriod;
                averageLoss += loss / rsiPeriod;
            } else {
                averageGain = (averageGain * (rsiPeriod - 1) + gain) / rsiPeriod;
                averageLoss = (averageLoss * (rsiPeriod - 1) + loss) / rsiPeriod;
            }
        }

        previousClose = close;

        if (changes < rsiPeriod) {
            return Double.NaN;
        }

        if (averageLoss == 0) {
            return averageGain == 0 ? 50 : 100;
        }

        return 100 - 100 / (1 + averageGain / averageLoss);
    }
}
