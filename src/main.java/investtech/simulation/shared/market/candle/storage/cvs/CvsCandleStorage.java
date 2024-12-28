package investtech.simulation.shared.market.candle.storage.cvs;

import investtech.simulation.shared.market.candle.Candle;
import investtech.simulation.shared.market.candle.CandleStorageInterface;
import investtech.simulation.shared.market.candle.FindPriceParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class CvsCandleStorage implements CandleStorageInterface, AutoCloseable {
    protected static final int READ_LIMIT = 1024 * 1024 * 1024;

    protected InputStream inputStream;

    protected String instrumentUid;

    public CvsCandleStorage(String instrumentUid, InputStream inputStream) {
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

        return Optional.of(candle);
    }

    @Override
    public Iterable<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections::emptyIterator;
        }

        resetInputStream(inputStream);

        return () -> new CvsCandleIterator(inputStream)
                .initInstrumentUid(instrumentUid)
                .initFrom(from)
                .initTo(to);
    }

    @Override
    public Iterable<Candle> findByOpenPrice(FindPriceParams params) {
        if (!Objects.equals(this.instrumentUid, params.getInstrumentUid())) {
            return Collections::emptyIterator;
        }

        resetInputStream(inputStream);

        Iterable<Candle> iterator = () -> new CvsCandleIterator(inputStream)
                .initInstrumentUid(instrumentUid)
                .initFrom(params.getFrom())
                .initTo(params.getTo());

        var resultCandles = new ArrayList<Candle>();

        for (Candle candle : iterator) {
            if (candle.getOpenPrice().compare(params.getPrice(), params.getComparisonOperator())) {
                resultCandles.add(candle);

                if (params.getMaxCount() <= resultCandles.size()) {
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
