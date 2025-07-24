package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

public class MarketDataServiceIT extends AbstractTinkoffSanboxIT {
    @Test
    public void getLastPrices() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var share = getTestShare();
        var marketDataService = tinkoffBroker.getMarketDataService();

        var lastPricesResponse = marketDataService.getLastPrices(GetLastPricesRequest.of(share.getUid()));

        for (var lastPrice : lastPricesResponse.getPrices()) {
            System.out.printf("price: %s\n", lastPrice.getPrice());
            System.out.printf("instrumentUid: %s\n", lastPrice.getInstrumentUid());
            System.out.printf("time: %s\n", lastPrice.getTime());
        }
    }

    @Test
    public void getCandles() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var share = getTestShare();
        var marketDataService = tinkoffBroker.getMarketDataService();

        var getCandlesResponse = marketDataService.getCandles(
            GetCandlesRequest.of(
                Instant.parse("2023-11-20T12:00:00.00Z"),
                Instant.parse("2024-01-01T12:00:00.00Z"),
                CandleInterval.DAY,
                share.getUid()
            )
        );

        for (var candle : getCandlesResponse.getCandles()) {
            System.out.printf("close: %s\n", candle.getClose());
            System.out.printf("open: %s\n", candle.getOpen());
            System.out.printf("high: %s\n", candle.getHigh());
            System.out.printf("low: %s\n", candle.getLow());
            System.out.printf("volume: %s\n", candle.getVolume());
            System.out.printf("time: %s\n", candle.getTime());
            System.out.println();
        }
    }
}
