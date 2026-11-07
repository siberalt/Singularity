package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Мера, написанная для широких баров, на узкой ленте: свечи сворачиваются в бары {@code interval}, мере
 * отдаются только <b>закрытые</b>, не больше {@code window} последних. Так дневной режим читается
 * часовой стратегией без заглядывания вперёд.
 *
 * <h2>Закрытый - значит пришла свеча следующего бара</h2>
 * Сам по себе бар не знает, что он кончился: у торгового дня нет последней свечи, которую видно заранее.
 * Поэтому день становится закрытым, когда пришла первая свеча следующего дня, и только тогда мера его
 * видит. Режим дня D стратегия узнаёт на первом часе дня D + 1 - как на дневном стенде, где решение
 * принимается на закрытии, а позиция держится со следующего бара.
 *
 * <h2>Чем это не {@link com.siberalt.singularity.strategy.signal.AggregatingSignalSource}</h2>
 * Тот между закрытиями молчит, потому что сигнал - событие: повторить его на каждой минуте значит
 * заплатить за одну сделку шестьдесят раз. Мера рынка - состояние, и между закрытиями она
 * <b>прежняя</b>: здесь отвечается последнее чтение, а мера пересчитывается только на новом закрытом
 * баре. До первого закрытого бара ответ {@link Double#NaN}.
 *
 * <h2>Состояние</h2>
 * Обёртка помнит, что видела, поэтому один экземпляр - одна бумага и один прогон. Ей можно отдавать и
 * ленту, и окно, повторяющее уже показанное: свеча не позже последней виденной пропускается, иначе окно
 * стратегии строило бы каждый день заново.
 */
public class ClosedBarsCoefficient implements MarketCoefficient {
    private final CandleInterval interval;

    private final int window;

    private final MarketCoefficient delegate;

    private final CandleAggregator aggregator = new CandleAggregator();

    private final ArrayDeque<Candle> closed = new ArrayDeque<>();

    private final List<Candle> openBar = new ArrayList<>();

    private long openBucket = Long.MIN_VALUE;

    private Instant lastSeen;

    private double reading = Double.NaN;

    /**
     * @param interval до какой ширины сворачивать; у неё должна быть постоянная длина
     * @param window   сколько последних закрытых баров отдавать мере
     * @param delegate мера, читающая широкие бары
     */
    public ClosedBarsCoefficient(CandleInterval interval, int window, MarketCoefficient delegate) {
        if (interval == null || interval == CandleInterval.UNSPECIFIED || interval == CandleInterval.MONTH) {
            throw new IllegalArgumentException("Сворачивать можно только в интервал постоянной длины, получено "
                + interval);
        }

        if (window < 1) {
            throw new IllegalArgumentException("Окно хотя бы в один бар, получено " + window);
        }

        if (delegate == null) {
            throw new IllegalArgumentException("Нужна мера, которой отдавать широкие бары");
        }

        this.interval = interval;
        this.window = window;
        this.delegate = delegate;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        boolean closedAny = false;

        for (Candle candle : lastCandles) {
            if (lastSeen != null && !candle.getTime().isAfter(lastSeen)) {
                continue;
            }

            lastSeen = candle.getTime();
            long bucket = aggregator.bucketOf(candle, interval);

            if (bucket != openBucket) {
                if (!openBar.isEmpty()) {
                    closed.addLast(aggregator.merge(openBar, interval));
                    closedAny = true;

                    if (closed.size() > window) {
                        closed.removeFirst();
                    }
                }

                openBar.clear();
                openBucket = bucket;
            }

            openBar.add(candle);
        }

        // Пачка из многих баров - начальная история стратегии - читается один раз, по последнему.
        if (closedAny) {
            reading = delegate.of(List.copyOf(closed));
        }

        return reading;
    }
}
