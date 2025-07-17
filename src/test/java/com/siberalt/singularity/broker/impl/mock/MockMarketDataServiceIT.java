package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.market.response.GetLastPricesResponse;
import com.siberalt.singularity.broker.contract.service.market.response.HistoricCandle;
import com.siberalt.singularity.broker.impl.mock.config.MockBrokerConfig;
import com.siberalt.singularity.entity.instrument.InstrumentRepository;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.test.util.ConfigLoader;
import com.siberalt.singularity.test.util.resource.ResourceHandler;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCandlesResponse;
import com.siberalt.singularity.broker.contract.service.market.response.LastPrice;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class MockMarketDataServiceIT {
    private MockMarketDataService marketDataService;
    private MockBroker broker;
    private ResourceHandler<CvsCandleRepository> storageHandler;
    private MockBrokerConfig config;
    private SimulationClock clock;

    @BeforeEach
    void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, MockBrokerConfig.SETTINGS_PATH);

        var instrument = new Instrument()
                .setCurrency(config.getInstrument().getCurrency())
                .setLot(config.getInstrument().getLot())
                .setInstrumentType(config.getInstrument().getInstrumentType())
                .setUid(config.getInstrument().getUid());

        InstrumentRepository instrumentRepository = new InMemoryInstrumentRepository();
        instrumentRepository.save(instrument);

        storageHandler = ResourceHandler.newHandler(() ->
                new CvsFileCandleRepositoryFactory().create(
                        config.getInstrument().getUid(),
                        config.getInstrument().getDataPath()
                )
        );

        clock = new SimpleSimulationClock();
        broker = new MockBroker(storageHandler.create(), instrumentRepository, null, clock);
        marketDataService = broker.getMarketDataService();
    }

    @AfterEach
    void tearDown() {
        storageHandler.close();
    }

    @Test
    void getCandlesTest() {
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:09:00Z"))
                        .setTo(Instant.parse("2020-12-30T07:17:00Z"))
                        .setInterval(CandleInterval.MIN_1)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:00:00Z"))
                        .setTo(Instant.parse("2020-12-30T07:17:00Z"))
                        .setInterval(CandleInterval.MIN_2)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:00:00Z"))
                        .setTo(Instant.parse("2020-12-30T15:00:00Z"))
                        .setInterval(CandleInterval.MIN_5)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:00:00Z"))
                        .setTo(Instant.parse("2020-12-30T15:00:00Z"))
                        .setInterval(CandleInterval.MIN_10)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:00:00Z"))
                        .setTo(Instant.parse("2020-12-30T15:00:00Z"))
                        .setInterval(CandleInterval.MIN_15)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-12-30T07:00:00Z"))
                        .setTo(Instant.parse("2020-12-30T15:00:00Z"))
                        .setInterval(CandleInterval.MIN_30)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2020-10-15T00:00:00Z"))
                        .setTo(Instant.parse("2020-10-30T00:00:00Z"))
                        .setInterval(CandleInterval.HOUR)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2021-12-15T00:00:00Z"))
                        .setTo(Instant.parse("2021-12-30T00:00:00Z"))
                        .setInterval(CandleInterval.HOUR_2)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2021-12-15T00:00:00Z"))
                        .setTo(Instant.parse("2021-12-30T00:00:00Z"))
                        .setInterval(CandleInterval.HOUR_4),
                0.6
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2021-12-01T00:00:00Z"))
                        .setTo(Instant.parse("2021-12-30T00:00:00Z"))
                        .setInterval(CandleInterval.DAY),
                0.7
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2021-10-30T00:00:00Z"))
                        .setTo(Instant.parse("2021-12-30T00:00:00Z"))
                        .setInterval(CandleInterval.WEEK)
        );
        getCandlesTest(
                new GetCandlesRequest()
                        .setInstrumentUid(config.getInstrument().getUid())
                        .setFrom(Instant.parse("2021-01-01T00:00:00Z"))
                        .setTo(Instant.parse("2021-05-30T00:00:00Z"))
                        .setInterval(CandleInterval.MONTH),
                0
        );
    }

    @Test
    void getLastPricesTest() {
        /*
          Tested file content:
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:12:00Z;5.562;5.558;5.562;5.558;798;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:13:00Z;5.56;5.56;5.56;5.56;55;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:14:00Z;5.56;5.56;5.56;5.56;56;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:15:00Z;5.558;5.56;5.56;5.556;35532;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:16:00Z;5.56;5.556;5.56;5.556;1605;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:17:00Z;5.56;5.558;5.56;5.558;195;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:18:00Z;5.56;5.56;5.56;5.558;13520;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:19:00Z;5.56;5.558;5.56;5.558;6099;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:20:00Z;5.56;5.562;5.562;5.558;27115;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:21:00Z;5.562;5.56;5.562;5.56;190;
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:22:00Z;5.558;5.56;5.56;5.558;10963;
         */
        GetLastPricesRequest request = new GetLastPricesRequest()
                .setInstrumentUid(config.getInstrument().getUid())
                .setPeriod(Duration.ofMinutes(10));
        clock.syncCurrentTime(Instant.parse("2020-12-30T15:22:00Z"));

        GetLastPricesResponse response = marketDataService.getLastPrices(request);

        assertNotNull(response);
        List<LastPrice> prices = response.getPrices();
        assertEquals(11, prices.size());

        // Assert first in list
        LastPrice firstPrice = prices.get(0);
        assertEquals(Instant.parse("2020-12-30T15:12:00Z"), firstPrice.getTime());
        assertEquals(Quotation.of(5.562), firstPrice.getPrice());

        // Assert last in list
        LastPrice lastPrice = prices.get(10);
        assertEquals(Instant.parse("2020-12-30T15:22:00Z"), lastPrice.getTime());
        assertEquals(Quotation.of(5.558), lastPrice.getPrice());

        // Assert correctness of time sequence
        Instant previousTime = firstPrice.getTime();
        prices.remove(0);

        for (LastPrice price : prices) {
            assertTrue(price.getTime().isAfter(previousTime));
            previousTime = price.getTime();
        }
    }

    @Test
    void getCandleAtTest() {
        /*
          Tested file content:
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:15:00Z;5.558;5.56;5.56;5.556;35532;
         */
        Instant time = Instant.parse("2020-12-30T15:15:00Z");
        Candle candle = marketDataService.getCandleAt(config.getInstrument().getUid(), time).orElseThrow();

        assertEquals(Instant.parse("2020-12-30T15:15:00Z"), candle.getTime());
        assertEquals(Quotation.of(5.558), candle.getOpenPrice());
        assertEquals(Quotation.of(5.56), candle.getClosePrice());
        assertEquals(Quotation.of(5.56), candle.getHighPrice());
        assertEquals(Quotation.of(5.556), candle.getLowPrice());
        assertEquals(35532, candle.getVolume());
    }

    @Test
    void findCandlesByClosePriceTest() {
        /* Tested file content:
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:25:00Z;5.556;5.556;5.556;5.554;709;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:26:00Z;5.556;5.556;5.556;5.554;140;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:27:00Z;5.554;5.556;5.556;5.554;2629;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:28:00Z;5.556;5.556;5.556;5.556;750;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:29:00Z;5.556;5.556;5.556;5.554;3532;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:30:00Z;5.556;5.556;5.556;5.554;23326;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:31:00Z;5.554;5.556;5.556;5.554;4558;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:32:00Z;5.556;5.554;5.556;5.554;202;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:33:00Z;5.556;5.556;5.556;5.554;1465;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:34:00Z;5.554;5.554;5.556;5.554;2020;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:35:00Z;5.556;5.554;5.556;5.554;9665;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:36:00Z;5.556;5.556;5.556;5.554;627;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:37:00Z;5.556;5.554;5.556;5.554;993;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:38:00Z;5.556;5.556;5.556;5.556;207;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:39:00Z;5.554;5.556;5.556;5.554;1978;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:40:00Z;5.556;5.556;5.556;5.554;1061;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:41:00Z;5.554;5.554;5.556;5.554;19324;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:42:00Z;5.556;5.556;5.556;5.554;3897;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:43:00Z;5.556;5.556;5.556;5.554;2612;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:44:00Z;5.556;5.556;5.556;5.554;2792;
        9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T14:45:00Z;5.556;5.556;5.556;5.556;611;
         */
        clock.syncCurrentTime(Instant.parse("2020-12-30T14:25:00Z"));
        FindPriceParams params = new FindPriceParams()
                .setInstrumentUid(config.getInstrument().getUid())
                .setMaxCount(4)
                .setFrom(Instant.parse("2020-12-30T14:25:00Z"))
                .setTo(Instant.parse("2020-12-30T14:45:00Z"))
                .setPrice(Quotation.of(5.554))
                .setComparisonOperator(ComparisonOperator.MORE);
        List<Candle> result = marketDataService.findCandlesByOpenPrice(CandleInterval.MIN_1, params);

        assertNotNull(result);
        assertEquals(4, result.size());

        // each candle has close price more than 5.554
        for (Candle candle : result) {
            assertTrue(candle.getClosePrice().isGreaterThan(Quotation.of(5.554)));
        }

        // Assert correctness of time sequence
        Instant previousTime = result.get(0).getTime();
        result.remove(0);

        for (Candle candle : result) {
            assertTrue(candle.getTime().isAfter(previousTime));
            previousTime = candle.getTime();
        }
    }

    @Test
    void instrumentLastCandleTest() {
        /*
          Tested file content:
          9654c2dd-6993-427e-80fa-04e80a1cf4da;2020-12-30T15:15:00Z;5.558;5.56;5.56;5.556;35532;
         */
        clock.syncCurrentTime(Instant.parse("2020-12-30T15:15:00Z"));
        Candle candle = marketDataService.getInstrumentCurrentCandle(config.getInstrument().getUid());

        assertNotNull(candle);
        assertEquals(Instant.parse("2020-12-30T15:15:00Z"), candle.getTime());
        assertEquals(Quotation.of(5.558), candle.getOpenPrice());
        assertEquals(Quotation.of(5.56), candle.getClosePrice());
        assertEquals(Quotation.of(5.56), candle.getHighPrice());
        assertEquals(Quotation.of(5.556), candle.getLowPrice());
        assertEquals(35532, candle.getVolume());
    }

    void getCandlesTest(GetCandlesRequest request) {
        getCandlesTest(request, 0.8);
    }

    void getCandlesTest(GetCandlesRequest request, double assertValidCandlesRatio) {
        GetCandlesResponse response = marketDataService.getCandles(request);
        Duration duration = request.getInterval().getDuration();

        // Assert response is not null
        assertNotNull(response);

        // Check interval between candles
        List<HistoricCandle> candles = response.getCandles();
        HistoricCandle previousCandle = null;

        int validCandlesCount = 0;

        for (HistoricCandle candle : candles) {
            if (null == previousCandle) {
                previousCandle = candle;
                continue;
            }

            assertTrue(candle.getTime().isAfter(previousCandle.getTime()));

            if (candle.getTime().equals(previousCandle.getTime().plus(duration))) {
                validCandlesCount++;
            }

            // Assert candle values are correct
            assertCandle(
                    previousCandle,
                    request.getInterval(),
                    request.getInstrumentUid(),
                    previousCandle.getTime(),
                    candle.getTime()
            );

            previousCandle = candle;
        }

        assertTrue(validCandlesCount >= candles.size() * assertValidCandlesRatio);
    }

    protected void assertCandle(
            HistoricCandle candle,
            CandleInterval assertInterval,
            String instrumentUid,
            Instant from,
            Instant to
    ) {
        var uniteCandles = StreamSupport
                .stream(
                        marketDataService
                                .candleRepository
                                .getPeriod(instrumentUid, from, to)
                                .spliterator(),
                        false
                )
                .collect(Collectors.toList());

        uniteCandles.remove(uniteCandles.size() - 1);
        assertTrue(uniteCandles.size() <= assertInterval.getDuration().toMinutes());

        System.out.println(candle.getTime() + ": test " + uniteCandles.size());

        Quotation openAvg = Quotation.of(BigDecimal.ZERO);
        Quotation closeAvg = Quotation.of(BigDecimal.ZERO);
        Quotation highAvg = Quotation.of(BigDecimal.ZERO);
        Quotation lowAvg = Quotation.of(BigDecimal.ZERO);
        long volumeAvg = 0;

        for (var uniteCandle : uniteCandles) {
            openAvg = openAvg.add(uniteCandle.getOpenPrice());
            closeAvg = closeAvg.add(uniteCandle.getClosePrice());
            highAvg = highAvg.add(uniteCandle.getHighPrice());
            lowAvg = lowAvg.add(uniteCandle.getLowPrice());
            volumeAvg += uniteCandle.getVolume();
        }

        openAvg = openAvg.divide(uniteCandles.size());
        closeAvg = closeAvg.divide(uniteCandles.size());
        highAvg = highAvg.divide(uniteCandles.size());
        lowAvg = lowAvg.divide(uniteCandles.size());
        volumeAvg /= uniteCandles.size();

        assertEquals(openAvg, candle.getOpen());
        assertEquals(closeAvg, candle.getClose());
        assertEquals(highAvg, candle.getHigh());
        assertEquals(lowAvg, candle.getLow());
        assertEquals(volumeAvg, candle.getVolume());
    }
}
