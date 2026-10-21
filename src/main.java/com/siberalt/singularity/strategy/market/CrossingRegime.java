package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Режим по частоте пересечений цены с кривой: если в окрестности их несколько - флэт, если пересечение
 * одиноко - сторона та, с которой цена сейчас стоит.
 *
 * <h2>Чем эта мера отличается от {@link SteadySide} над {@link TrendAngle}</h2>
 * Тем, что считает <b>знаки, а не величины</b>, и отсюда её главное свойство: <b>ей не нужна шкала</b>.
 * Ни рублей за бар, ни ATR, ни постоянной при корне - только «цена выше кривой или ниже» и сколько раз
 * это менялось. Каждая прежняя мера режима спотыкалась об эту постоянную, и в этой базе измерено, во
 * что обходится её произвол: доля флэта по углу средней вышла 8%, по углу цены 44%, по амплитуде ног
 * 69-80% - три разумных критерия, три несовместимых ответа.
 * <p>
 * И это не та же мера в другой записи, в отличие от разметки по экстремумам средней. Там работает
 * тождество {@code sma[t] − sma[t−1] = (p[t] − p[t−N]) / N}: средняя разворачивается, когда цена
 * пересекает <i>саму себя</i> N баров назад. Здесь событие другое - цена пересекает <i>среднюю</i>.
 *
 * <h2>Причинность и лаг</h2>
 * Само пересечение известно в тот же бар, подтверждать его нечем - в отличие от экстремума, которому
 * нужна земля справа (в этой базе медиана подтверждения 12 баров плюс отставание самой средней).
 * Задержка здесь ровно одна и назначается явно: окрестность {@code window}, в которой считаются
 * пересечения. Ничего не ждёт дольше неё.
 *
 * <h2>Что измерено про дельту между пересечениями</h2>
 * Подтверждать тренд ещё и размером хода между пересечениями здесь нарочно нечем, и причина
 * измеренная. В точке пересечения цена равна кривой по определению, поэтому ход между двумя
 * пересечениями - это с точностью до одного бара {@code кривая[t₂] − кривая[t₁]}, то есть та же дельта
 * средней, снятая в других точках. А мерить её бесполезно: дельта растёт медленнее корня из
 * промежутка - делённая на него, она падает с 0.44 у коротких отрезков до 0.25 у длинных, - так что
 * длинный отрезок не быстрее короткого, а медленнее. Размер хода тренд не подтверждает.
 */
public class CrossingRegime implements MarketCoefficient {
    private final MarketSeries level;

    private final int window;

    private final int crowd;

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param level  кривая, которую пересекает цена; обычно средняя
     * @param window окрестность в барах, в которой считаются пересечения
     * @param crowd  сколько пересечений в окрестности делают её флэтом; минимум два - одно
     *               пересечение частотой не является
     */
    public CrossingRegime(MarketSeries level, int window, int crowd) {
        if (level == null) {
            throw new IllegalArgumentException("Нужна кривая, пересечения с которой считать");
        }

        if (window < 2) {
            throw new IllegalArgumentException(
                "Окрестность нужна хотя бы в два бара, получено " + window);
        }

        if (crowd < 2) {
            throw new IllegalArgumentException(
                "Гроздь - это два пересечения и больше, получено " + crowd);
        }

        this.level = level;
        this.window = window;
        this.crowd = crowd;
    }

    /** Сравнивает с кривой не закрытие, а что-нибудь ещё - типичную цену, скажем. */
    public CrossingRegime setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, какую цену сравнивать с кривой");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Double.NaN;
        }

        double[] curve = level.of(lastCandles);
        int last = curve.length - 1;

        if (last < 0 || Double.isNaN(curve[last])) {
            return Double.NaN;
        }

        int crossings = 0;
        double previous = 0;

        for (int at = Math.max(0, curve.length - window); at <= last; at++) {
            if (Double.isNaN(curve[at])) {
                continue;
            }

            double side = Math.signum(sideOf(lastCandles, at, curve));

            // Цена ровно на кривой - не сторона: засчитать её значило бы получить два пересечения там,
            // где цена всего лишь коснулась и пошла дальше тем же путём.
            if (side == 0) {
                continue;
            }

            if (previous != 0 && side != previous) {
                crossings++;
            }

            previous = side;
        }

        if (previous == 0) {
            return Double.NaN;
        }

        return crossings >= crowd ? TrendZone.FLAT : previous;
    }

    private double sideOf(List<Candle> lastCandles, int at, double[] curve) {
        return priceExtractor.extract(lastCandles.get(at)).toDouble() - curve[at];
    }
}
