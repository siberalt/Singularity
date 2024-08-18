package investtech.emulation.shared.market.candle.storage.cvs;

import investtech.broker.contract.value.quatation.Quotation;
import investtech.emulation.shared.market.candle.Candle;

import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Scanner;
import java.util.regex.Pattern;

class CvsCandleIterator implements Iterator<Candle> {
    protected Scanner scanner;
    protected Instant to;
    protected Instant currentTime;

    public CvsCandleIterator(InputStream inputStream) {
        scanner = new Scanner(inputStream);
    }

    @Override
    public boolean hasNext() {
        return currentTime.isBefore(to) && scanner.hasNext();
    }

    @Override
    public Candle next() {
        String[] data = scanner.nextLine().split(";");
        currentTime = Instant.parse(data[1]);

        return new Candle()
                .setInstrumentUid(data[0])
                .setTime(currentTime)
                .setOpenPrice(Quotation.of(data[2]))
                .setClosePrice(Quotation.of(data[3]))
                .setHighPrice(Quotation.of(data[4]))
                .setLowPrice(Quotation.of(data[5]))
                .setVolume(Long.parseLong(data[6]));
    }

    public CvsCandleIterator initFrom(Instant from) {
        Instant time;

        do {
            if (!hasNext()) {
                // TODO: throw exception
            }

            var matcher = Pattern.compile("^.*;(.*);").matcher(scanner.nextLine());

            if (!matcher.find()) {
                // TODO: throw exception
            }

            time = Instant.parse(matcher.group(0));
        } while (time.isBefore(from));

        return this;
    }

    public CvsCandleIterator initTo(Instant to) {
        this.to = to;
        return this;
    }
}
