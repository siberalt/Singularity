package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Свечи другого интервала поверх тех, что лежат в базе: минутки на входе, часы или дни на выходе.
 * <p>
 * Нужно это симуляции. Событийный брокер проигрывает ровно то, что отдаёт репозиторий, и подписка на свечи
 * интервала не знает — значит стратегия видит минутки, и только их. Правилу на минутах это подходит, а
 * правилу на дневных средних нет: чтобы собрать SMA(200) по дням из минуток, стратегии пришлось бы держать
 * в окне сто двадцать тысяч свечей и пересчитывать среднюю на каждом баре. Проще отдать ей сразу дневные.
 * <p>
 * Поэтому агрегация здесь, а не в стратегии: в симуляции меняется источник, и весь остальной путь —
 * подписка, заявки, заливы, часы — остаётся тем же. Заодно и часы симуляции начинают идти днями, что для
 * правила с удержанием в месяцы и есть правильная гранулярность.
 * <p>
 * Чего это не делает и о чём надо помнить: внутридневной информации после агрегации нет. Заявка,
 * исполняемая по дневной свече, заливается по её цене, а не по той минуте, когда рынок действительно
 * доходил до уровня, — значит стоп внутри дня здесь моделируется грубее, чем на минутках. Для правила, у
 * которого сигнал считается по закрытиям дней, это цена, которую стоит платить; для минутного правила этот
 * класс бессмыслен.
 */
public class AggregatingCandleRepository implements ReadCandleRepository {
    private final ReadCandleRepository source;
    private final CandleInterval interval;
    private final CandleAggregator aggregator = new CandleAggregator();

    public AggregatingCandleRepository(ReadCandleRepository source, CandleInterval interval) {
        if (source == null || interval == null) {
            throw new IllegalArgumentException("Нужны источник свечей и интервал");
        }

        this.source = source;
        this.interval = interval;
    }

    @Override
    public List<Candle> getPeriod(long instrumentId, Instant from, Instant to) {
        return aggregator.aggregate(source.getPeriod(instrumentId, from, to), interval);
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(long instrumentId, Instant from, Instant to) {
        return source.getRangeMetadata(instrumentId, from, to);
    }

    @Override
    public Optional<Candle> getAt(long instrumentId, Instant at) {
        List<Candle> bucket = getPeriod(instrumentId, bucketStart(at), bucketStart(at).plusMillis(width()));

        return bucket.isEmpty() ? Optional.empty() : Optional.of(bucket.getFirst());
    }

    /**
     * Столько завершённых свечей укрупнённого интервала, сколько попросили.
     * <p>
     * Исходных свечей берётся с запасом — интервал умножается на ширину и ещё вдвое, потому что в сутках
     * бывают выходные, а в часе перерыв: без запаса за «двести дней назад» вернулось бы полторы сотни.
     */
    @Override
    public List<Candle> findBeforeOrEqual(long instrumentId, Instant at, long amountBefore) {
        List<Candle> aggregated = aggregator.aggregate(
            source.findBeforeOrEqual(instrumentId, at, amountBefore * barsInside() * 2), interval);

        return aggregated.size() <= amountBefore ? aggregated
            : aggregated.subList(aggregated.size() - (int) amountBefore, aggregated.size());
    }

    @Override
    public List<Candle> findAfterOrEqual(long instrumentId, Instant at, long amountAfter) {
        List<Candle> aggregated = aggregator.aggregate(
            source.findAfterOrEqual(instrumentId, at, amountAfter * barsInside() * 2), interval);

        return aggregated.size() <= amountAfter ? aggregated : aggregated.subList(0, (int) amountAfter);
    }

    /**
     * Поиск по цене остаётся на исходных свечах, а найденное укрупняется.
     * <p>
     * Так и надо: вопрос «когда рынок дошёл до уровня» имеет смысл на той гранулярности, на которой он
     * туда доходил. Спросить его у дневной свечи значит узнать, что уровень был задет когда-то за день.
     */
    @Override
    public List<Candle> findByPrice(long instrumentId, FindPriceParams params) {
        return aggregator.aggregate(source.findByPrice(instrumentId, params), interval);
    }

    private long width() {
        return interval.getDuration().toMillis();
    }

    /** Сколько минутных свечей умещается в одном укрупнённом интервале - оценка сверху для запаса. */
    private long barsInside() {
        return Math.max(1, width() / 60_000);
    }

    private Instant bucketStart(Instant at) {
        return Instant.ofEpochMilli(Math.floorDiv(at.toEpochMilli(), width()) * width());
    }
}
