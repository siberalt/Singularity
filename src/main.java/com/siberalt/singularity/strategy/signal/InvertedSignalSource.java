package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Takes another calculator's answer and bets the other way.
 * <p>
 * Which way an instrument's moves go is a property of the instrument - some carry on, some come
 * back, and {@link com.siberalt.singularity.strategy.analysis.VarianceRatio} tells them apart. A
 * signal built for one kind is not useless on the other; it is pointing the wrong way, and turning
 * it round is the shortest way to ask whether it knows anything at all.
 * <p>
 * Mostly that question is worth asking of a measurement rather than of a strategy. An inverted
 * signal keeps everything about the original except its sign, including the gates it was given for
 * its own purpose: inverting {@link SlopeSignalSource} keeps its goodness-of-fit filter, which
 * selects for straight lines, so the result trades the ends of clean trends rather than anything a
 * reversion bet would have chosen for itself. Where the bet itself is the point,
 * {@link MeanReversionSignalSource} makes it directly. Where the question is whether the
 * original signal has content, this is the control that answers it - and a signal that loses
 * exactly as much inverted as it made upright is a signal, while one that loses both ways is noise
 * plus costs.
 * <p>
 * Silence stays silent: a calculator with no opinion has none inverted either, and the strength it
 * reported is carried through untouched - turning a signal round says nothing about how sure it was.
 */
public class InvertedSignalSource implements SignalSource {
    private final SignalSource delegate;

    public InvertedSignalSource(SignalSource delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Nothing to invert");
        }

        this.delegate = delegate;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Signal signal = delegate.calculate(lastCandles);

        // Handed back as it came rather than negated to minus zero, which is a different value to
        // anything comparing whole answers and the same one to everything else.
        if (signal.confidence() == 0) {
            return signal;
        }

        // Тип и доля счёта переносятся как есть: инверсия - про сторону, а не про то, вход это или
        // выход и сколько просят. Пересборка через двухаргументный конструктор их обнулила бы.
        return new Signal(
            signal.type(),
            -signal.confidence(),
            signal.strength(),
            signal.positionBalance()
        );
    }
}
