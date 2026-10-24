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
 * где {@code sw} - наклон регрессии по раннему отрезку, {@code sl} - по позднему. Отрезки
 * <b>не пересекаются</b>: ранний кончается там, где начинается поздний.
 *
 * <h2>Ноль здесь значит «тренд ровный», а не «тренда нет»</h2>
 * Это главное, что надо знать, прежде чем куда-нибудь это ставить. Прямой устойчивый рост даёт
 * <b>ноль</b>: наклоны отрезков равны, меняться нечему. Единица означает, что отрезки смотрят в разные
 * стороны, то есть ход развернулся внутри окна. Про то, вверх рынок идёт или вниз, эта мера не говорит
 * ничего - для этого есть {@link ChangeAngleSignalSource}, {@link SlopeSignalSource} и крест.
 * <p>
 * Поэтому её место - <b>выход</b>, а не вход: выдыхающийся тренд это повод закрыть позицию, а не
 * открыть. Направление в этой базе измерено до дна - парные различия между крестом, наклоном EMA и
 * MACD в двух видах ни одно не проходит двух ошибок, - а кривизну не мерили ни разу.
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
 * <h2>Отрезки разной длины - это другая мера, а не настройка</h2>
 * Длины задаются по отдельности, и короткий поздний отрезок против длинного раннего - вещь осмысленная:
 * устойчивая база против чувствительного щупа. Но читать такую пару как симметричное сравнение нельзя.
 * <p>
 * Разброс наклона регрессии падает примерно как {@code n³}, поэтому при отрезках 30 и 10 ошибка
 * короткого наклона выше примерно <b>в пять раз</b>, и почти вся изменчивость ответа приходит от него.
 * Мера тогда отвечает не «ход ускорился», а «последние десять баров отличаются от обычного хода»; это
 * не хуже, но это другой вопрос. Равные отрезки задаются конструктором с одним числом.
 * <p>
 * Та же асимметрия портит и силу: {@code R²} на коротком отрезке завышен - через три-четыре точки
 * прямая проходит почти всегда, - а сила берёт наименьшее из двух, то есть обычно как раз короткое.
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
    private final int earlierBars;

    private final int laterBars;

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param earlierBars сколько баров в раннем отрезке - в базе, с которой сравнивают
     * @param laterBars   сколько в позднем - в том, что сравнивают с базой
     */
    public SlopeChangeSignalSource(int earlierBars, int laterBars) {
        this.earlierBars = checked(earlierBars, "ранний");
        this.laterBars = checked(laterBars, "поздний");
    }

    /** Отрезки равной длины: симметричное сравнение, где изменчивость приходит от обоих поровну. */
    public SlopeChangeSignalSource(int barsPerSlope) {
        this(barsPerSlope, barsPerSlope);
    }

    /** Читает ход не по закрытию, а по чему-нибудь ещё - по типичной цене, скажем. */
    public SlopeChangeSignalSource setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, по какой цене проводить наклон");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    /** Сколько свечей нужно, чтобы мера посчиталась. */
    public int requiredCandles() {
        return earlierBars + laterBars;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < requiredCandles()) {
            return Signal.NEUTRAL;
        }

        int last = lastCandles.size();
        LinearRegression earlier = fit(lastCandles, last - requiredCandles(), earlierBars);
        LinearRegression later = fit(lastCandles, last - laterBars, laterBars);
        double scale = Math.abs(later.getSlope()) + Math.abs(earlier.getSlope());
        // Оба наклона нулевые - ход стоит, и ускоряться нечему. Ноль здесь не «не знаю», а ответ.
        double change = scale == 0 ? 0 : (later.getSlope() - earlier.getSlope()) / scale;

        return new Signal(change, Math.min(earlier.getR2(), later.getR2()));
    }

    private static int checked(int bars, String which) {
        if (bars < 3) {
            throw new IllegalArgumentException(
                "Наклон нужно проводить хотя бы по трём барам, " + which + " отрезок: " + bars);
        }

        return bars;
    }

    /** Регрессия по отрезку. Сдвиг по оси x на наклон не влияет, поэтому она всегда с нуля. */
    private LinearRegression fit(List<Candle> lastCandles, int from, int length) {
        double[] x = new double[length];
        double[] y = new double[length];

        for (int at = 0; at < length; at++) {
            x[at] = at;
            y[at] = priceExtractor.extract(lastCandles.get(from + at)).toDouble();
        }

        return new LinearRegression(x, y);
    }
}
