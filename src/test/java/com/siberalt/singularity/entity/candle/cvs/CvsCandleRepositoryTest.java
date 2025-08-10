package com.siberalt.singularity.entity.candle.cvs;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public class CvsCandleRepositoryTest {
    static class TimeOrderAsserter {
        Instant lastTime = null;

        public void assertTime(Instant newTime) {
            if (null != lastTime) {
                Assertions.assertTrue(lastTime.isBefore(newTime), "Time disorder");
            }
            lastTime = newTime;
        }
    }

    protected static final String SETTINGS_PATH = "src/test/resources/entity.candle.cvs/test-settings.yaml";

    @Test
    void testGetAt() throws IOException {
        var config = createTestConfig();
        String instrumentUid = config.getInstrumentUid();
        String instrumentDataPath = config.getInstrumentDataPath();
        var candleStorageFactory = new CvsFileCandleRepositoryFactory();

        try (var candleStorage = candleStorageFactory.create(instrumentUid, instrumentDataPath)) {
            // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-10T09:45:00Z;5.436;5.436;5.436;5.434;1588;
            assertCandleEquals(
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-10T09:45:00Z"))
                    .setOpenPrice(Quotation.of(5.436))
                    .setClosePrice(Quotation.of(5.436))
                    .setHighPrice(Quotation.of(5.436))
                    .setLowPrice(Quotation.of(5.434))
                    .setVolume(1588)
                ,
                candleStorage.getAt(instrumentUid, Instant.parse("2020-12-10T09:45:00Z"))
            );

            // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-10T10:40:00Z;5.44;5.436;5.44;5.436;1750;
            assertCandleEquals(
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-10T10:40:00Z"))
                    .setOpenPrice(Quotation.of(5.44))
                    .setClosePrice(Quotation.of(5.436))
                    .setHighPrice(Quotation.of(5.44))
                    .setLowPrice(Quotation.of(5.436))
                    .setVolume(1750)
                ,
                candleStorage.getAt(instrumentUid, Instant.parse("2020-12-10T10:40:00Z"))
            );

            // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-10T11:33:00Z;5.44;5.442;5.442;5.44;785;
            assertCandleEquals(
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-10T11:33:00Z"))
                    .setOpenPrice(Quotation.of(5.44))
                    .setClosePrice(Quotation.of(5.442))
                    .setHighPrice(Quotation.of(5.442))
                    .setLowPrice(Quotation.of(5.44))
                    .setVolume(785)
                ,
                candleStorage.getAt(instrumentUid, Instant.parse("2020-12-10T11:33:00Z"))
            );
        }
    }

    @Test
    void testGetPeriod() throws IOException {
        var config = createTestConfig();
        String instrumentUid = config.getInstrumentUid();
        String instrumentDataPath = config.getInstrumentDataPath();
        var candleStorageFactory = new CvsFileCandleRepositoryFactory();

        try (var candleStorage = candleStorageFactory.create(instrumentUid, instrumentDataPath)) {
            List<Candle> matchCandles = List.of(
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-09-02T07:00:00Z;4.97;4.968;4.97;4.948;255;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-09-02T07:00:00Z"))
                    .setOpenPrice(Quotation.of(4.97))
                    .setClosePrice(Quotation.of(4.968))
                    .setHighPrice(Quotation.of(4.97))
                    .setLowPrice(Quotation.of(4.948))
                    .setVolume(255),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-09-02T07:11:00Z;4.968;4.968;4.968;4.966;8572;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-09-02T07:11:00Z"))
                    .setOpenPrice(Quotation.of(4.968))
                    .setClosePrice(Quotation.of(4.968))
                    .setHighPrice(Quotation.of(4.968))
                    .setLowPrice(Quotation.of(4.966))
                    .setVolume(8572),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-09-02T11:59:00Z;4.99;4.99;4.99;4.99;584;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-09-02T11:59:00Z"))
                    .setOpenPrice(Quotation.of(4.99))
                    .setClosePrice(Quotation.of(4.99))
                    .setHighPrice(Quotation.of(4.99))
                    .setLowPrice(Quotation.of(4.99))
                    .setVolume(584),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-09-02T15:40:00Z;4.916;4.918;4.918;4.91;710;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-09-02T15:40:00Z"))
                    .setOpenPrice(Quotation.of(4.916))
                    .setClosePrice(Quotation.of(4.918))
                    .setHighPrice(Quotation.of(4.918))
                    .setLowPrice(Quotation.of(4.91))
                    .setVolume(710)
            );

            matchCandles = new ArrayList<>(matchCandles);
            Collections.reverse(matchCandles);

            assertPeriod(
                candleStorage,
                instrumentUid,
                Instant.parse("2020-09-02T07:00:00Z"),
                Instant.parse("2020-09-02T15:40:00Z"),
                matchCandles
            );

            matchCandles = List.of(
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-25T07:00:00Z;5.466;5.464;5.466;5.454;570;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-25T07:00:00Z"))
                    .setOpenPrice(Quotation.of(5.466))
                    .setClosePrice(Quotation.of(5.464))
                    .setHighPrice(Quotation.of(5.466))
                    .setLowPrice(Quotation.of(5.454))
                    .setVolume(570),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-25T08:11:00Z;5.454;5.452;5.454;5.452;4373;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-25T08:11:00Z"))
                    .setOpenPrice(Quotation.of(5.454))
                    .setClosePrice(Quotation.of(5.452))
                    .setHighPrice(Quotation.of(5.454))
                    .setLowPrice(Quotation.of(5.452))
                    .setVolume(4373),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-25T09:41:00Z;5.454;5.454;5.454;5.454;104;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-25T09:41:00Z"))
                    .setOpenPrice(Quotation.of(5.454))
                    .setClosePrice(Quotation.of(5.454))
                    .setHighPrice(Quotation.of(5.454))
                    .setLowPrice(Quotation.of(5.454))
                    .setVolume(104),
                // 9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-25T15:44:00Z;5.48;5.482;5.482;5.48;343;
                new Candle()
                    .setInstrumentUid(instrumentUid)
                    .setTime(Instant.parse("2020-12-25T15:44:00Z"))
                    .setOpenPrice(Quotation.of(5.48))
                    .setClosePrice(Quotation.of(5.482))
                    .setHighPrice(Quotation.of(5.482))
                    .setLowPrice(Quotation.of(5.48))
                    .setVolume(343)
            );

            matchCandles = new ArrayList<>(matchCandles);
            Collections.reverse(matchCandles);

            assertPeriod(
                candleStorage,
                instrumentUid,
                Instant.parse("2020-12-25T07:00:00Z"),
                Instant.parse("2020-12-25T15:44:00Z"),
                matchCandles
            );

            System.out.println("end");
        }
    }

    @Test
    void testFindByOpenPrice() throws IOException {
        var config = createTestConfig();
        String instrumentUid = config.getInstrumentUid();
        String instrumentDataPath = config.getInstrumentDataPath();
        var candleStorageFactory = new CvsFileCandleRepositoryFactory();

        try (var candleStorage = candleStorageFactory.create(instrumentUid, instrumentDataPath)) {
            assertFindByOpenPrice(
                candleStorage,
                new FindPriceParams(
                    instrumentUid,
                    Instant.parse("2020-09-07T07:06:00Z"),
                    Instant.parse("2020-09-07T15:40:00Z"),
                    Quotation.of(4.89),
                    ComparisonOperator.MORE,
                    5
                )
            );

            assertFindByOpenPrice(
                candleStorage,
                new FindPriceParams(
                    instrumentUid,
                    Instant.parse("2020-12-30T07:00:00Z"),
                    Instant.parse("2020-12-30T15:44:00Z"),
                    Quotation.of(4.89),
                    ComparisonOperator.LESS,
                    5
                )
            );

            assertFindByOpenPrice(
                candleStorage,
                new FindPriceParams(
                    instrumentUid,
                    Instant.parse("2020-12-30T07:00:00Z"),
                    Instant.parse("2020-12-30T15:44:00Z"),
                    Quotation.of(5.55),
                    ComparisonOperator.LESS,
                    5
                )
            );
        }
    }

    CvsTestConfig createTestConfig() throws IOException {
        try (var configurationStream = Files.newInputStream(Paths.get(SETTINGS_PATH))) {
            ConfigInterface configuration = new YamlConfig(configurationStream);
            String instrumentUid = (String) configuration.get("instrumentUid");
            String instrumentDataPath = (String) configuration.get("instrumentDataPath");

            return new CvsTestConfig(instrumentUid, instrumentDataPath);
        }
    }

    void assertFindByOpenPrice(CvsCandleRepository candleStorage, FindPriceParams findPriceParams) {
        var candles = candleStorage.findByOpenPrice(findPriceParams);

        var totalCount = 0;
        var timeOrderAsserter = new TimeOrderAsserter();

        for (var candle : candles) {
            var candleTime = candle.getTime();
            Assertions.assertTrue(candleTime.compareTo(findPriceParams.from()) >= 0);
            Assertions.assertTrue(candleTime.compareTo(findPriceParams.to()) <= 0);
            Assertions.assertTrue(
                candle.getOpenPrice().compare(
                    findPriceParams.price(),
                    findPriceParams.comparisonOperator()
                )
            );
            timeOrderAsserter.assertTime(candle.getTime());
            totalCount++;
        }

        Assertions.assertTrue(totalCount <= findPriceParams.maxCount());
    }

    void assertPeriod(
        CvsCandleRepository candleStorage,
        String instrumentUid,
        Instant from,
        Instant to,
        List<Candle> matchCandles
    ) {
        var stack = new Stack<Candle>();
        stack.addAll(matchCandles);

        if (stack.empty()) {
            Assertions.fail("No match candles");
        }

        var matchCandle = stack.pop();
        TimeOrderAsserter timeOrderAsserter = new TimeOrderAsserter();

        for (var candle : candleStorage.getPeriod(instrumentUid, from, to)) {
            timeOrderAsserter.assertTime(candle.getTime());

            if (candle.getTime().equals(matchCandle.getTime())) {
                assertCandleEquals(candle, matchCandle);

                if (stack.empty()) {
                    return;
                }

                matchCandle = stack.pop();
            }
        }

        Assertions.fail("There are unmatched candles left");
    }

    void assertCandleEquals(Candle actual, Optional<Candle> expectedOptional) {
        Assertions.assertTrue(expectedOptional.isPresent());
        assertCandleEquals(actual, expectedOptional.get());
    }

    void assertCandleEquals(Candle actual, Candle expected) {
        Assertions.assertEquals(actual.getInstrumentUid(), expected.getInstrumentUid());
        Assertions.assertEquals(actual.getTime(), expected.getTime());
        Assertions.assertEquals(actual.getOpenPrice(), expected.getOpenPrice());
        Assertions.assertEquals(actual.getClosePrice(), expected.getClosePrice());
        Assertions.assertEquals(actual.getHighPrice(), expected.getHighPrice());
        Assertions.assertEquals(actual.getLowPrice(), expected.getLowPrice());
        Assertions.assertEquals(actual.getVolume(), expected.getVolume());
    }
}
