package com.siberalt.singularity.simulation.shared.market.candle.storage.cvs;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;

import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Scanner;
import java.util.regex.Pattern;

class CvsCandleIterator implements Iterator<Candle> {
    protected Scanner scanner;
    protected Instant to;
    protected Instant currentTime;
    protected String instrumentUid;
    protected String initFromLastLine = null;

    public CvsCandleIterator(InputStream inputStream) {
        scanner = new Scanner(inputStream);
    }

    @Override
    public boolean hasNext() {
        return currentTime.isBefore(to) && scanner.hasNext();
    }

    @Override
    public Candle next() {
        String line;

        if (null != initFromLastLine) {
            line = initFromLastLine;
            initFromLastLine = null;
        } else {
            line = scanner.nextLine();
        }

        String[] data = line.split(";");
        currentTime = Instant.parse(data[1]);

        return new Candle()
                .setInstrumentUid(instrumentUid == null ? data[0] : instrumentUid)
                .setTime(currentTime)
                .setOpenPrice(Quotation.of(data[2]))
                .setClosePrice(Quotation.of(data[3]))
                .setHighPrice(Quotation.of(data[4]))
                .setLowPrice(Quotation.of(data[5]))
                .setVolume(Long.parseLong(data[6]));
    }

    public CvsCandleIterator initFrom(Instant from) {
        currentTime = null;

        do {
            if (!scanner.hasNext()) {
                // TODO: throw exception
            }

            var matcher = Pattern.compile("^.+?;(.+?);").matcher(initFromLastLine = scanner.nextLine());

            if (!matcher.find()) {
                // TODO: throw exception
            }

            currentTime = Instant.parse(matcher.group(1));
        } while (currentTime.isBefore(from));

        return this;
    }

    public CvsCandleIterator initTo(Instant to) {
        this.to = to;
        return this;
    }

    public CvsCandleIterator initInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }
}
