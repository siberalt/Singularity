package com.siberalt.singularity.entity.candle.cvs;

import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.candle.Candle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.*;

public class CvsCandleRepository implements ReadCandleRepository, AutoCloseable {
    protected static final int READ_LIMIT = 1024 * 1024 * 1024;

    private final InputStream inputStream;
    private final String instrumentUid;

    public CvsCandleRepository(String instrumentUid, InputStream inputStream) {
        this.inputStream = inputStream;
        this.instrumentUid = instrumentUid;
        this.inputStream.mark(READ_LIMIT);
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Optional.empty();
        }

        resetInputStream(inputStream);

        var candle = new CvsCandleIterator(inputStream)
            .initInstrumentUid(instrumentUid)
            .initFrom(at)
            .next();

        return Optional.ofNullable(candle);
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);
        CvsCandleIterator iterator = new CvsCandleIterator(inputStream).initInstrumentUid(instrumentUid);

        ArrayDeque<Candle> candlesBuffer = new ArrayDeque<>();

        while (iterator.hasNext()) {
            Candle candle = iterator.next();

            if (candle.getTime().isAfter(at)) {
                // If the candle time is after the requested time, we stop searching
                break;
            }

            if (candle.getTime().equals(at)) {
                candlesBuffer.removeFirst();
                candlesBuffer.addLast(candle);

                return candlesBuffer.stream().toList();
            }

            candlesBuffer.addLast(candle);

            if (candlesBuffer.size() > amountBefore) {
                candlesBuffer.removeFirst();
            }
        }

        return candlesBuffer.stream().toList();
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);

        CvsCandleIterator iterator = new CvsCandleIterator(inputStream)
            .initInstrumentUid(instrumentUid)
            .initFrom(from)
            .initTo(to);
        List<Candle> resultCandles = new ArrayList<>();

        while (iterator.hasNext()) {
            resultCandles.add(iterator.next());
        }

        return resultCandles;
    }

    @Override
    public List<Candle> findByOpenPrice(FindPriceParams params) {
        if (!Objects.equals(this.instrumentUid, params.instrumentUid())) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);

        Iterable<Candle> iterator = () -> new CvsCandleIterator(inputStream)
            .initInstrumentUid(instrumentUid)
            .initFrom(params.from())
            .initTo(params.to());

        var resultCandles = new ArrayList<Candle>();

        for (Candle candle : iterator) {
            if (candle.getOpenPrice().compare(params.price(), params.comparisonOperator())) {
                resultCandles.add(candle);

                if (params.maxCount() <= resultCandles.size()) {
                    break;
                }
            }
        }

        return resultCandles;
    }

    protected void resetInputStream(InputStream inputStream) {
        try {
            inputStream.reset();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
