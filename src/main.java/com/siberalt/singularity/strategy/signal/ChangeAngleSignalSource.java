package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Угол хода по приращениям - и такой же угол по объёму рядом с ним. Детектор мощного движения.
 * <p>
 * Якоря здесь нет и процентов здесь нет, в отличие от {@link PriceChangeSignalSource}: там мерится
 * размер смещения от экстремума окна, здесь - насколько ход был <b>односторонним</b>. Пять процентов,
 * набранные одним рывком, и те же пять процентов, набранные через дёрганье туда-обратно, в процентах
 * одинаковы, а как движение - нет.
 *
 * <h2>Прямоугольник и его угол</h2>
 * Вертикаль - сумма приращений, делённая на число баров, то есть чистый ход за бар. Горизонталь - один бар.
 * Но тангенс требует, чтобы обе стороны были в одних единицах, а «рубль» и «бар» несравнимы, поэтому
 * вертикаль нормируется <b>средним абсолютным приращением за бар</b>:
 * <pre>
 *     tg(угол) = (Σ dᵢ / N) / (Σ |dᵢ| / N) = Σ dᵢ / Σ |dᵢ|
 * </pre>
 * Отсюда всё остальное. По неравенству треугольника {@code |Σ dᵢ| ≤ Σ |dᵢ|}, значит тангенс лежит в
 * [−1, 1], а <b>угол никогда не выходит за ±45°</b>: ровно 45° означает, что все бары шли в одну сторону,
 * ноль - что ход туда и обратно сошёлся в ничью. Прямоугольник получает жёсткую шкалу, и бумага за 100 ₽
 * становится сравнимой с бумагой за 10000 ₽.
 * <p>
 * Величина {@code Σ dᵢ / Σ |dᵢ|} - это коэффициент эффективности Кауфмана, а угол - его монотонная
 * перепараметризация. Названо прямо, чтобы не выглядело выдумкой: новое здесь не сама мера, а то, что рядом
 * с ней той же мерой читается объём.
 * <p>
 * Буквальное чтение «сумма изменений на число баров, и угол от этого» дало бы рубли за бар под
 * {@code atan}, то есть угол, зависящий от цены бумаги. Такой угол нельзя ни сравнить между бумагами, ни
 * выставить порогом один раз - поэтому он здесь и не считается.
 *
 * <h2>Чем это не {@link SlopeSignalSource}</h2>
 * Тот берёт наклон линейной регрессии по самим ценам и гейтит по R². Наклон у него в рублях за бар
 * (несравним между бумагами), а R² отвечает на вопрос «насколько ход похож на прямую». Здесь не прямизна, а
 * односторонность, и шкала безразмерна. Две разные вещи, и выбирать между ними надо измерением.
 *
 * <h2>Что уходит в сигнал, а что в силу</h2>
 * <b>Сигнал</b> - угол цены, выраженный долей от своего предела: {@code угол / 45°}, то есть ровно [−1, 1],
 * как и просит контракт {@link Signal}. Единица - все бары вверх, минус единица - все вниз.
 * <p>
 * <b>Сила</b> - угол объёма, переложенный в [0, 1]: единица - объём рос на каждом баре, половина - стоял
 * или дёргался в ничью, ноль - падал на каждом баре. В [0, 1], а не в [−1, 1], потому что силой в этой базе
 * уже меряют размер позиции, и отрицательная сила там означала бы не «объём падает», а ерунду.
 * <p>
 * <b>Порога «мощное движение» внутри нет, и это нарочно.</b> Сигнал равен нормированному углу, поэтому
 * «только мощные» выражается порогом стратегии: {@code setBuyThreshold(0.6)} и есть «не меньше 27°». Второе
 * место, где написано то же самое, рано или поздно разойдётся с первым - это уже измерено на мёртвой зоне.
 * Требование «и объём растёт» ставится так же снаружи: {@link FilterSignalSource} с условием на
 * {@code strength}.
 * <p>
 * Нулевой сигнал при высокой силе - это не молчание, а сведение: объём набирается, а цена никуда не идёт.
 * Поэтому при достаточной истории здесь всегда возвращается пара чисел, а {@link Signal#NEUTRAL} означает
 * ровно одно - истории не хватило.
 */
public class ChangeAngleSignalSource implements SignalSource {
    /** Предел угла: больше 45° не бывает, см. неравенство треугольника в описании класса. */
    public static final double MAX_DEGREES = 45;

    private final int bars;

    private PriceExtractor priceExtractor = Candle::close;

    private ToDoubleFunction<Candle> volumeExtractor = Candle::volume;

    /**
     * @param bars сколько приращений входит в прямоугольник; свечей нужно на одну больше
     */
    public ChangeAngleSignalSource(int bars) {
        if (bars < 1) {
            throw new IllegalArgumentException(
                "Угол нужно мерить хотя бы по одному приращению, получено " + bars);
        }

        this.bars = bars;
    }

    /** Читает ход не по закрытию, а по чему-нибудь ещё - по типичной цене, скажем. */
    public ChangeAngleSignalSource setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;

        return this;
    }

    /**
     * Читает объём не целиком, а по его части - например {@link Candle#volumeBuy()}, если вопрос в том, с
     * какой стороны приходит объём.
     */
    public ChangeAngleSignalSource setVolumeExtractor(ToDoubleFunction<Candle> volumeExtractor) {
        this.volumeExtractor = volumeExtractor;

        return this;
    }

    /** Оба угла в градусах, каждый в [−45, 45]. Отдельно от {@link #calculate}, чтобы их было чем читать. */
    public Angles anglesOf(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < bars + 1) {
            return null;
        }

        List<Candle> window = lastCandles.subList(lastCandles.size() - bars - 1, lastCandles.size());

        return new Angles(
            angleOf(window, candle -> priceExtractor.extract(candle).toDouble()),
            angleOf(window, volumeExtractor)
        );
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Angles angles = anglesOf(lastCandles);

        if (angles == null) {
            return Signal.NEUTRAL;
        }

        return new Signal(angles.price() / MAX_DEGREES, (angles.volume() / MAX_DEGREES + 1) / 2);
    }

    /** Угол цены и угол объёма в одном окне, в градусах. */
    public record Angles(double price, double volume) {
    }

    /**
     * Угол по приращениям величины внутри окна, в градусах.
     * <p>
     * Ноль возвращается в двух разных случаях - ход сошёлся в ничью и величина не менялась вовсе, - и
     * различать их здесь нечем: у неподвижной величины направления нет, так что ноль для неё и есть
     * правильный ответ.
     */
    private static double angleOf(List<Candle> window, ToDoubleFunction<Candle> value) {
        double net = 0;
        double travelled = 0;

        for (int at = 1; at < window.size(); at++) {
            double change = value.applyAsDouble(window.get(at))
                - value.applyAsDouble(window.get(at - 1));

            net += change;
            travelled += Math.abs(change);
        }

        if (travelled == 0) {
            return 0;
        }

        return Math.toDegrees(Math.atan(net / travelled));
    }
}
