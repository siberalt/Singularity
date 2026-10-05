package com.siberalt.singularity.strategy.upside.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.IncrementalRsi;
import com.siberalt.singularity.strategy.upside.SignalCondition;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;
import java.util.function.Supplier;

/**
 * Покупать только внизу, продавать только наверху: лонг пропускается при RSI не выше уровня, шорт - при RSI
 * не ниже.
 * <p>
 * Условие асимметричное, и именно поэтому оно не {@link
 * com.siberalt.singularity.strategy.market.MarketCondition}: один и тот же RSI пропускает одну сторону и
 * запрещает другую, так что без стороны сигнала его не проверить. Отсюда и {@link SignalCondition} - он
 * спрашивает сигнал, но только затем, чтобы узнать знак.
 * <p>
 * Смысл поверх трендового сигнала - вход на откате: тренд восходящий, но брать не на самом пересечении, а
 * когда цена откатилась к середине своего диапазона. Поверх возвратного сигнала смысл обратный - это
 * подтверждение, что инструмент действительно перепродан.
 * <p>
 * <b>Оговорка, которая уже измерена в этом проекте.</b> Ожидание отката на трендовом входе стоит денег:
 * фильтр «покупать ниже VWAP(50)» отбросил две трети сделок и срезал избыток с +1603 до +267 базисных
 * пунктов, а вход по следующему закрытию оказался хуже входа по открытию. Причина общая - после
 * пересечения цена обычно выше своей средней, и ждать возврата к ней значит ждать слабости. Так что от
 * этого условия на трендовом сигнале стоит ждать меньше сделок, и проверять надо, не стали ли оставшиеся
 * настолько лучше, чтобы это оплатить. Условие здесь для того, чтобы такую проверку было чем сделать.
 * <p>
 * Пока RSI не набрал период, условие не выполнено: проверить его нечем, а фильтр, который в такой момент
 * пропускает всё, - это отсутствие фильтра на самом нужном участке, на первых барах истории.
 */
public class RsiSide implements SignalCondition {
    private final int period;

    private final double buyBelow;

    private final double sellAbove;

    /**
     * @param period    период RSI
     * @param buyBelow  выше этого уровня лонг не пропускается
     * @param sellAbove ниже этого уровня не пропускается шорт
     */
    public RsiSide(int period, double buyBelow, double sellAbove) {
        // Период проверяет сам индикатор - дублировать его правила значит заводить второе место, где
        // написано, какие периоды допустимы.
        new IncrementalRsi(period);
        checkLevel(buyBelow);
        checkLevel(sellAbove);

        this.period = period;
        this.buyBelow = buyBelow;
        this.sellAbove = sellAbove;
    }

    /** Один уровень на обе стороны: покупать в нижней половине, продавать в верхней. */
    public RsiSide(int period) {
        this(period, IncrementalRsi.BALANCED, IncrementalRsi.BALANCED);
    }

    @Override
    public boolean holds(List<Candle> lastCandles, Supplier<Upside> signal) {
        Upside upside = signal.get();

        if (upside == null || upside.signal() == 0) {
            // Нет стороны - нечего разрешать или запрещать, а наружу всё равно уйдёт молчание делегата.
            return true;
        }

        double rsi = IncrementalRsi.of(lastCandles, period);

        if (Double.isNaN(rsi)) {
            return false;
        }

        return upside.signal() > 0 ? rsi <= buyBelow : rsi >= sellAbove;
    }

    private static void checkLevel(double level) {
        if (!(level >= 0) || level > 100) {
            throw new IllegalArgumentException("RSI лежит в [0, 100], уровень " + level + " недостижим");
        }
    }
}
