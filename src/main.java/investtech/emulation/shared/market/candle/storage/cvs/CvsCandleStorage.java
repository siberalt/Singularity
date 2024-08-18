package investtech.emulation.shared.market.candle.storage.cvs;

import investtech.emulation.shared.market.candle.Candle;
import investtech.emulation.shared.market.candle.CandleStorageInterface;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class CvsCandleStorage implements CandleStorageInterface {
    protected InputStream inputStream;

    protected String instrumentUid;

    public CvsCandleStorage(String instrumentUid, InputStream inputStream) {
        this.inputStream = inputStream;
        this.instrumentUid = instrumentUid;
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Optional.empty();
        }

        var candle = new CvsCandleIterator(inputStream)
                .initFrom(at)
                .next();

        return Optional.of(candle);
    }

    @Override
    public Iterable<Candle> getTo(String instrumentUid, Instant to) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections::emptyIterator;
        }

        return () -> new CvsCandleIterator(inputStream)
                .initTo(to);
    }

    @Override
    public Iterable<Candle> getFrom(String instrumentUid, Instant from) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections::emptyIterator;
        }

        return () -> new CvsCandleIterator(inputStream)
                .initFrom(from);
    }

    @Override
    public Iterable<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        if (!Objects.equals(this.instrumentUid, instrumentUid)) {
            return Collections::emptyIterator;
        }

        return () -> new CvsCandleIterator(inputStream)
                .initFrom(from);
    }
}
