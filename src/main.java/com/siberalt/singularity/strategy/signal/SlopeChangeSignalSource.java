package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

/**
 * Насколько ход <b>ускорился или выдохся</b>: наклон позднего отрезка против наклона раннего. Мера
 * кривизны, а не направления.
 * <pre>
 *     сигнал = (sl − sw) / (|sl| + |sw|)
 * </pre>
 * где {@code sw} - наклон регрессии по раннему отрезку, {@code sl} - по позднему. Откуда берутся сами
 * отрезки, решает {@link StretchPolicy}: {@link StretchPolicy#fixed} нарезает их по счёту баров,
 * {@link StretchPolicy#betweenPivots} якорит на разворотах цены. Мера от этого не меняется - меняется
 * только то, что она сравнивает.
 *
 * <h2>Ноль здесь значит «тренд ровный», а не «тренда нет»</h2>
 * Это главное, что надо знать, прежде чем куда-нибудь это ставить. Прямой устойчивый рост даёт
 * <b>ноль</b>: наклоны отрезков равны, меняться нечему. Единица означает, что отрезки смотрят в разные
 * стороны, то есть ход развернулся между ними. Про то, вверх рынок идёт или вниз, эта мера не говорит
 * ничего - для этого есть {@link ChangeAngleSignalSource}, {@link SlopeSignalSource} и крест.
 * <p>
 * Поэтому её место - <b>выход</b>, а не вход: выдыхающийся тренд это повод закрыть позицию, а не
 * открыть. Направление в этой базе измерено до дна - парные различия между крестом, наклоном EMA и
 * MACD в двух видах ни одно не проходит двух ошибок, - а кривизну не мерили ни разу.
 *
 * <h2>Предел достигается чаще, чем кажется</h2>
 * Стоит знать до выставления порогов: ровно ±1 выходит на 48% баров при отрезках 20 и 20 и на 53% при
 * 120 и 120, а медиана модуля там 0.95 и 1.00. Предел означает лишь, что знаки наклонов разошлись, то
 * есть между отрезками случился разворот. Порог в районе 0.7 поэтому не отсекает хвост, а проходится
 * чаще, чем нет - проверено на ликвидной трети.
 *
 * <h2>Почему знаменатель именно такой</h2>
 * Напрашивающееся {@code (sl − sw) / sw} - деление на знаковую величину, и оно ломается дважды.
 * <b>Знак переворачивается на всех падениях:</b> при {@code sw = −3, sl = −1} падение замедляется, то
 * есть ход поворачивает вверх, а формула даёт минус; при {@code sw = −1, sl = −3} идёт ускоряющийся
 * обвал, а формула даёт плюс. И <b>мера взрывается во флэте</b>, потому что {@code sw} проходит через
 * ноль на каждом развороте - то есть растёт до бесконечности ровно там, где информации нет.
 * <p>
 * Сумма модулей лечит оба: знаменатель положителен, в ноль обращается только вместе с числителем, а по
 * неравенству треугольника {@code |sl − sw| ≤ |sl| + |sw|}, так что результат лежит в {@code [−1, 1]}
 * без всякого {@code tanh} и инвариант {@link Signal} выполняется по построению.
 *
 * <h2>Нормировать наклоны на цену не нужно, и это не экономия</h2>
 * Мера однородна нулевой степени: домножьте оба наклона на любое положительное число, и ответ не
 * изменится. Значит бумага за 100 ₽ и бумага за 10000 ₽ дают сравнимые числа сами собой, и деление на
 * цену - которое пришлось бы делать у {@link SlopeSignalSource}, где наклон остаётся в рублях за бар и
 * между бумагами несравним, - здесь просто сократилось бы.
 *
 * <h2>Наклон считается регрессией, а не разностью концов</h2>
 * Средняя дельта по отрезку телескопируется в {@code (последняя − первая) / n} и слепа к пути: отрезок,
 * прошедший прямо, и отрезок, продёргавшийся туда-обратно к той же точке, дали бы одно число. Регрессия
 * использует все точки, и ради этого отличия мера и строится.
 *
 * <h2>Что уходит в силу</h2>
 * Наименьшее из двух {@code R²} - насколько оба отрезка вообще похожи на прямые. Наклон, проведённый
 * сквозь дёрганый отрезок, формально существует, но сравнивать его со вторым таким же бессмысленно, и
 * сила об этом и сообщает.
 * <p>
 * <b>Порога на силу внутри нет, и это нарочно.</b> «Только когда оба отрезка прямые» ставится снаружи
 * через {@link FilterSignalSource} с условием на {@link Signal#strength()}. Второе место, где написано
 * то же самое, рано или поздно разойдётся с первым - в этой базе это уже измерено на мёртвой зоне.
 */
public class SlopeChangeSignalSource implements SignalSource {
    /** Меньше трёх точек прямую проводить не по чему. */
    public static final int MIN_SLOPE_BARS = 3;

    private final StretchPolicy stretches;

    private final int minSlopeBars;

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param stretches    чем выбирать отрезки
     * @param minSlopeBars сколько баров нужно каждому отрезку; короче - источник молчит
     */
    public SlopeChangeSignalSource(StretchPolicy stretches, int minSlopeBars) {
        if (stretches == null) {
            throw new IllegalArgumentException("Нужно, чем выбирать отрезки под наклоны");
        }

        if (minSlopeBars < MIN_SLOPE_BARS) {
            throw new IllegalArgumentException(
                "Отрезку нужно хотя бы " + MIN_SLOPE_BARS + " бара, получено " + minSlopeBars);
        }

        this.stretches = stretches;
        this.minSlopeBars = minSlopeBars;
    }

    public SlopeChangeSignalSource(StretchPolicy stretches) {
        this(stretches, MIN_SLOPE_BARS);
    }

    /** Отрезки заданной длины, отсчитанные от конца окна. */
    public SlopeChangeSignalSource(int earlierBars, int laterBars) {
        this(StretchPolicy.fixed(earlierBars, laterBars));
    }

    /** Отрезки равной длины: симметричное сравнение. */
    public SlopeChangeSignalSource(int barsPerSlope) {
        this(StretchPolicy.fixed(barsPerSlope));
    }

    /** Читает ход не по закрытию, а по чему-нибудь ещё - по типичной цене, скажем. */
    public SlopeChangeSignalSource setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, по какой цене проводить наклон");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        SlopeStretches chosen = stretches.of(lastCandles);

        if (chosen == null || !chosen.longEnough(minSlopeBars)
            || chosen.laterTo() >= lastCandles.size()) {
            return Signal.NEUTRAL;
        }

        LinearRegression earlier = fit(lastCandles, chosen.earlierFrom(), chosen.earlierTo());
        LinearRegression later = fit(lastCandles, chosen.laterFrom(), chosen.laterTo());

        return new Signal(changeOf(earlier.getSlope(), later.getSlope()),
            Math.min(earlier.getR2(), later.getR2()));
    }

    /**
     * Ядро меры, вынесенное отдельно, чтобы у формулы и её обоснования был один экземпляр.
     * <p>
     * Ноль возвращается, когда оба наклона нулевые: ход стоит, и ускоряться нечему.
     */
    public static double changeOf(double earlierSlope, double laterSlope) {
        double scale = Math.abs(laterSlope) + Math.abs(earlierSlope);

        return scale == 0 ? 0 : (laterSlope - earlierSlope) / scale;
    }

    /** Регрессия по отрезку; обе границы входят. Сдвиг по оси x на наклон не влияет. */
    private LinearRegression fit(List<Candle> lastCandles, int from, int to) {
        int length = to - from + 1;
        double[] x = new double[length];
        double[] y = new double[length];

        for (int at = 0; at < length; at++) {
            x[at] = at;
            y[at] = priceExtractor.extract(lastCandles.get(from + at)).toDouble();
        }

        return new LinearRegression(x, y);
    }
}
