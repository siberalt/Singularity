package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Развороты цены, подтверждённые <b>откатом</b>: экстремум объявляется в тот момент, когда цена ушла от
 * него на {@code threshold} волатильностей.
 *
 * <h2>Чем отличается от остальных локаторов здесь</h2>
 * Двумя вещами, и обе стоит знать до того, как ставить его в цепочку.
 * <p>
 * <b>Он отдаёт минимумы и максимумы вперемешку</b>, по очереди, в хронологическом порядке - потому что
 * разворот тем и разворот, что чередуется. Остальные локаторы этого пакета односторонние:
 * {@code ofMinimums} ищет только низы. Контракт {@link ExtremeLocator} этого не запрещает, но тот, кто
 * привык к односторонним, споткнётся.
 * <p>
 * <b>Ему не нужен {@link TrailingMarginExtremeLocator}.</b> Локатор, сравнивающий бар с соседями, не
 * может судить о конце окна - бары, которые решили бы дело, не пришли, - и в общей с кем-то памяти его
 * хвост становится заглядыванием вперёд. Здесь хвоста нет по построению: пока откат не состоялся,
 * экстремум не объявлен вовсе, и ни один возвращённый разворот не опирается на бары после себя.
 *
 * <h2>Цена причинности измерена</h2>
 * По ликвидной трети: от самого экстремума до бара, на котором его удаётся подтвердить, проходит медиана
 * в 1 бар при пороге 0.5, 3 бара при 1.0 и 7 при 2.0. Для сравнения, подтверждение экстремума SMA(50)
 * окрестностью стоило в этой же базе медианы в 12 баров плюс отставание самой средней примерно в 25.
 *
 * <h2>Порог нельзя занизить</h2>
 * Он решает всё, и вот чем это меряется - длиной отрезка от последнего разворота до конца окна:
 * <pre>
 *     порог 0     медиана 1 бар   (то есть «разворот на каждом баре»)
 *     порог 0.5   медиана 4 бара
 *     порог 1.0   медиана 9 баров
 *     порог 2.0   медиана 20 баров
 * </pre>
 * Дневные и часовые числа совпадают почти в точности, то есть порог в волатильностях переносится между
 * таймфреймами - редкое свойство, которого нет ни у порога в процентах, ни у порога в барах.
 */
public class ZigZagExtremeLocator implements ExtremeLocator {
    /** При нём отрезок от последнего разворота выходит медианой в девять баров. */
    public static final double DEFAULT_THRESHOLD = 1;

    private final VolatilityCalculator volatilityCalculator;

    private final double threshold;

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param volatilityCalculator чем мерить откат; берётся {@link VolatilityCalculator#profile}, то
     *                             есть волатильность каждого бара, а не одна на всё окно
     * @param threshold            на сколько волатильностей цена должна отойти от экстремума
     */
    public ZigZagExtremeLocator(VolatilityCalculator volatilityCalculator, double threshold) {
        if (volatilityCalculator == null) {
            throw new IllegalArgumentException("Нужно, чем мерить откат");
        }

        if (!(threshold > 0)) {
            throw new IllegalArgumentException(
                "Порог отката положителен, иначе разворотом окажется каждый бар, получено " + threshold);
        }

        this.volatilityCalculator = volatilityCalculator;
        this.threshold = threshold;
    }

    public ZigZagExtremeLocator(double threshold) {
        this(new ATRVolatilityCalculator(), threshold);
    }

    public ZigZagExtremeLocator() {
        this(DEFAULT_THRESHOLD);
    }

    /** Читает разворот не по закрытию, а по чему-нибудь ещё - по типичной цене, скажем. */
    public ZigZagExtremeLocator setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, по какой цене искать развороты");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        List<Candle> pivots = new ArrayList<>();

        if (candles == null || candles.size() < 2) {
            return pivots;
        }

        double[] volatility = volatilityCalculator.profile(candles);
        int direction = 0;
        int extremeAt = 0;
        double extreme = priceOf(candles, 0);

        for (int at = 1; at < candles.size(); at++) {
            double price = priceOf(candles, at);

            if (direction >= 0 && price > extreme) {
                extreme = price;
                extremeAt = at;
                direction = 1;
            } else if (direction <= 0 && price < extreme) {
                extreme = price;
                extremeAt = at;
                direction = -1;
            } else if (extremeAt != at && Math.abs(price - extreme) > threshold * volatility[at]) {
                // Откат состоялся: экстремум лежит позади, и с этого бара он больше не изменится.
                pivots.add(candles.get(extremeAt));
                direction = -direction;
                extreme = price;
                extremeAt = at;
            }
        }

        return pivots;
    }

    private double priceOf(List<Candle> candles, int at) {
        return priceExtractor.extract(candles.get(at)).toDouble();
    }
}
