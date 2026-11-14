package com.siberalt.singularity.strategy.signal;

/**
 * Как {@link SlopeChangeSignalSource} сводит два наклона - раннего отрезка и позднего - в одно число в
 * [−1, 1]. Знак - куда загибается ход, величина - насколько.
 * <p>
 * Обе формулы здесь однородны нулевой степени - умножьте оба наклона на положительное число, и ответ не
 * изменится, - поэтому бумаги с разной ценой сравнимы без деления на цену. И обе дают ноль на ровном тренде,
 * где наклоны равны.
 *
 * <h2>Чем формулы различаются</h2>
 * <table>
 *     <tr><th>ранний → поздний</th><th>{@link #SUM}</th><th>{@link #MIRROR}</th></tr>
 *     <tr><td>1 → −1, зеркало</td><td>−1</td><td>−1</td></tr>
 *     <tr><td>1 → −0.1, слабый разворот</td><td>−1</td><td>−0.55</td></tr>
 *     <tr><td>0.1 → −3, резкий поздний</td><td>−1</td><td>−0.52</td></tr>
 *     <tr><td>1 → 0, остановка</td><td>−1</td><td>−0.5</td></tr>
 *     <tr><td>1 → 0.1, замедление</td><td>−0.82</td><td>−0.45</td></tr>
 *     <tr><td>0.1 → 3, ускорение</td><td>+0.94</td><td>+0.48</td></tr>
 * </table>
 */
@FunctionalInterface
public interface SlopeChangeFormula {
    /**
     * {@code (sl − sw) / (|sl| + |sw|)} - по умолчанию. Любая смена знака наклона даёт ровно ±1, какой бы ни
     * была величина: на ликвидной трети предел - на 48% баров при отрезках 20 и 20 и на 53% при 120 и 120.
     * Порог около 0.9 поэтому читается как «знак сменился».
     */
    SlopeChangeFormula SUM = (earlier, later) -> {
        double scale = Math.abs(later) + Math.abs(earlier);

        return scale == 0 ? 0 : (later - earlier) / scale;
    };

    /**
     * {@code (sl − sw) / (2 · max(|sl|, |sw|))} - зеркальная. Ровно ±1 только у зеркала, {@code sl = −sw}, и
     * затухает при асимметрии в обе стороны: резкий поздний отрезок при слабом раннем сигнал не усиливает.
     * Остановка, замедление и ускорение остаются, но вполсилы. В [−1, 1] по построению:
     * {@code |sl − sw| ≤ |sl| + |sw| ≤ 2 · max}. Порог 0.9 здесь - поздний наклон от 0.8 до 1.25 зеркала
     * раннего.
     */
    SlopeChangeFormula MIRROR = (earlier, later) -> {
        double scale = 2 * Math.max(Math.abs(later), Math.abs(earlier));

        return scale == 0 ? 0 : (later - earlier) / scale;
    };

    /**
     * @param earlierSlope наклон раннего отрезка
     * @param laterSlope   наклон позднего
     * @return в [−1, 1]; ноль, когда оба наклона нулевые
     */
    double of(double earlierSlope, double laterSlope);
}
