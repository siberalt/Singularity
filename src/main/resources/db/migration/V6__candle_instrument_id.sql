-- Свечи и чекпойнты миграции начинают ссылаться на наш instrument(id) вместо
-- строкового идентификатора инструмента у брокера.
--
-- Зачем: uid - это ключ конкретного брокера, а не свойство бумаги. Пока он лежал
-- в candle, одна и та же бумага у двух брокеров была двумя разными рядами свечей,
-- а по самому ряду нельзя было сказать, что это за бумага: в базе лежали вперемешку
-- uid'ы и тикер TMOS. Таблицы instrument и instrument_broker_listing для этого и
-- заводились (V2), но до сих пор оставались пустыми.
--
-- Что делаем со старыми данными: для каждого uid, встречающегося в свечах или
-- чекпойнтах и ещё не имеющего листинга, заводится инструмент-заглушка с именем,
-- равным uid, и типом UNSPECIFIED. Настоящие имя, ISIN, лот и валюту проставит
-- загрузчик (FetchTinkoffCandles) при следующем обращении к этой бумаге - здесь
-- их взять неоткуда, а терять уже загруженные свечи ради этого нельзя.

INSERT INTO instrument (isin, name, instrument_type)
SELECT NULL, uids.uid, 'UNSPECIFIED'
FROM (
    SELECT DISTINCT instrument_uid AS uid FROM candle
    UNION
    SELECT DISTINCT instrument_uid AS uid FROM candle_migration_range
) AS uids
WHERE uids.uid NOT IN (SELECT broker_instrument_id FROM instrument_broker_listing);

INSERT INTO instrument_broker_listing (instrument_id, broker_id, broker_instrument_id, lot, currency)
SELECT i.id, 'tinkoff', i.name, 1, 'RUB'
FROM instrument i
WHERE i.name IN (
        SELECT DISTINCT instrument_uid FROM candle
        UNION
        SELECT DISTINCT instrument_uid FROM candle_migration_range
    )
  AND NOT EXISTS (
        SELECT 1 FROM instrument_broker_listing l WHERE l.broker_instrument_id = i.name
    );

-- SQLite не умеет ни менять тип колонки, ни переносить UNIQUE через ALTER TABLE,
-- поэтому обе таблицы пересоздаются - как это уже делалось в V5.
CREATE TABLE candle_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    instrument_id INTEGER NOT NULL REFERENCES instrument(id) ON DELETE CASCADE,
    time_index INTEGER NOT NULL,
    time INTEGER NOT NULL,
    open_price INTEGER NOT NULL,
    close_price INTEGER NOT NULL,
    high_price INTEGER NOT NULL,
    low_price INTEGER NOT NULL,
    volume INTEGER NOT NULL,
    volume_buy INTEGER NOT NULL DEFAULT 0,
    volume_sell INTEGER NOT NULL DEFAULT 0,
    UNIQUE(instrument_id, time)
);

INSERT INTO candle_new (id, instrument_id, time_index, time, open_price, close_price, high_price, low_price, volume, volume_buy, volume_sell)
SELECT c.id, l.instrument_id, c.time_index, c.time, c.open_price, c.close_price, c.high_price, c.low_price, c.volume, c.volume_buy, c.volume_sell
FROM candle c
JOIN instrument_broker_listing l ON l.broker_instrument_id = c.instrument_uid;

DROP TABLE candle;
ALTER TABLE candle_new RENAME TO candle;

CREATE INDEX IF NOT EXISTS idx_candle_instrument_time ON candle(instrument_id, time);
CREATE INDEX IF NOT EXISTS idx_candle_time ON candle(time);

CREATE TABLE candle_migration_range_new (
    instrument_id INTEGER NOT NULL REFERENCES instrument(id) ON DELETE CASCADE,
    range_from INTEGER NOT NULL,
    range_to INTEGER NOT NULL,
    PRIMARY KEY (instrument_id, range_from)
);

INSERT INTO candle_migration_range_new (instrument_id, range_from, range_to)
SELECT l.instrument_id, r.range_from, r.range_to
FROM candle_migration_range r
JOIN instrument_broker_listing l ON l.broker_instrument_id = r.instrument_uid;

DROP TABLE candle_migration_range;
ALTER TABLE candle_migration_range_new RENAME TO candle_migration_range;
