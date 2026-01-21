package com.siberalt.singularity.entity.candle.cvs;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;

import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.regex.Pattern;

class CvsCandleIterator implements Iterator<Candle> {
    private final Scanner scanner;
    private Instant to;
    private Instant currentTime;
    private String instrumentUid;
    private String initFromLastLine = null;
    private long currentIndex = -1;

    public CvsCandleIterator(InputStream inputStream) {
        scanner = new Scanner(inputStream);
    }

    @Override
    public boolean hasNext() {
        return (currentTime == null || to == null || currentTime.isBefore(to)) && scanner.hasNext();
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

        try {
            String[] data = line.split(";");
            currentTime = Instant.parse(data[1]);

            return new Candle()
                .setIndex(++currentIndex)
                .setInstrumentUid(instrumentUid == null ? data[0] : instrumentUid)
                .setTime(currentTime)
                .setOpenPrice(Quotation.of(data[2]))
                .setClosePrice(Quotation.of(data[3]))
                .setHighPrice(Quotation.of(data[4]))
                .setLowPrice(Quotation.of(data[5]))
                .setVolume(Long.parseLong(data[6]));
        } catch (DateTimeParseException exception) {
            System.out.println("Invalid date format in line: " + line);
            return this.next();
        }
    }

    public CvsCandleIterator initFrom(Instant from) {
        currentTime = null;

        do {
            if (!scanner.hasNext()) {
                throw new RuntimeException("No more lines in the input stream");
            }

            var matcher = Pattern.compile("^.+?;(.+?);").matcher(initFromLastLine = scanner.nextLine());

            if (!matcher.find()) {
                throw new RuntimeException("Invalid line format: " + initFromLastLine);
            }

            currentIndex++;
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
