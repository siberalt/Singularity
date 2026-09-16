package com.siberalt.singularity.entity.candle.cvs;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleRangeMetadata;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.shared.TimePointRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.*;

public class CvsCandleRepository implements ReadCandleRepository, AutoCloseable {
    protected static final int READ_LIMIT = 1024 * 1024 * 1024;

    private final InputStream inputStream;
    private final long instrumentId;

    public CvsCandleRepository(long instrumentId, InputStream inputStream) {
        this.inputStream = inputStream;
        this.instrumentId = instrumentId;
        this.inputStream.mark(READ_LIMIT);
    }

    @Override
    public Optional<Candle> getAt(long instrumentId, Instant at) {
        if (this.instrumentId != instrumentId) {
            return Optional.empty();
        }

        resetInputStream(inputStream);

        var candle = new CvsCandleIterator(inputStream)
            .initInstrumentId(instrumentId)
            .initFrom(at)
            .next();

        return Optional.ofNullable(candle);
    }

    @Override
    public List<Candle> findBeforeOrEqual(long instrumentId, Instant at, long amountBefore) {
        if (this.instrumentId != instrumentId) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);
        CvsCandleIterator iterator = new CvsCandleIterator(inputStream).initInstrumentId(instrumentId);

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
    public List<Candle> findAfterOrEqual(long instrumentId, Instant at, long amountAfter) {
        if (this.instrumentId != instrumentId) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);
        CvsCandleIterator iterator = new CvsCandleIterator(inputStream).initInstrumentId(instrumentId).initFrom(at);

        List<Candle> resultCandles = new ArrayList<>();

        while (iterator.hasNext() && resultCandles.size() < amountAfter) {
            resultCandles.add(iterator.next());
        }

        return resultCandles;
    }

    @Override
    public List<Candle> getPeriod(long instrumentId, Instant from, Instant to) {
        if (this.instrumentId != instrumentId) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);

        CvsCandleIterator iterator = new CvsCandleIterator(inputStream)
            .initInstrumentId(instrumentId)
            .initFrom(from)
            .initTo(to);
        List<Candle> resultCandles = new ArrayList<>();

        while (iterator.hasNext()) {
            resultCandles.add(iterator.next());
        }

        return resultCandles;
    }

    @Override
    public List<Candle> findByPrice(long instrumentId, FindPriceParams params) {
        if (this.instrumentId != instrumentId) {
            return Collections.emptyList();
        }

        resetInputStream(inputStream);

        Iterable<Candle> iterator = () -> new CvsCandleIterator(inputStream)
            .initInstrumentId(instrumentId)
            .initFrom(params.from())
            .initTo(params.to());

        var resultCandles = new ArrayList<Candle>();

        for (Candle candle : iterator) {
            if (params.priceField().of(candle).compare(params.price(), params.comparisonOperator())) {
                resultCandles.add(candle);

                if (params.maxCount() <= resultCandles.size()) {
                    break;
                }
            }
        }

        return resultCandles;
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(long instrumentId, Instant from, Instant to) {
        if (this.instrumentId != instrumentId) {
            return CandleRangeMetadata.EMPTY;
        }

        resetInputStream(inputStream);

        int count = 0;
        Instant lastTime = null, firstTime = null;

        Iterable<Candle> iterator = () -> new CvsCandleIterator(inputStream)
            .initInstrumentId(instrumentId)
            .initFrom(from)
            .initTo(to);

        for (Candle candle : iterator) {
            if (count == 0) {
                firstTime = candle.getTime();
            }
            count++;
            lastTime = candle.getTime();
        }

        if (count == 0 || lastTime == null) {
            return CandleRangeMetadata.EMPTY;
        }

        TimePointRange range = new TimePointRange(new TimePoint(firstTime), new TimePoint(lastTime));
        return new CandleRangeMetadata(range, count);
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
