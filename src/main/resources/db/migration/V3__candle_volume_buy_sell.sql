-- Разбивка объёма свечи на покупки/продажи (Tinkoff HistoricCandle.volume_buy/volume_sell)
ALTER TABLE candle ADD COLUMN volume_buy INTEGER NOT NULL DEFAULT 0;
ALTER TABLE candle ADD COLUMN volume_sell INTEGER NOT NULL DEFAULT 0;
