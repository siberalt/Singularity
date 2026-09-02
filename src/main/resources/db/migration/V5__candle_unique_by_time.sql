-- Ключ дедупликации свечи меняется с (instrument_uid, time_index) на
-- (instrument_uid, time). При параллельной миграции чанков time_index
-- на момент вставки ещё не имеет смысла (проставляется placeholder,
-- реальное значение считается отдельным шагом нормализации после
-- миграции) - значит, идентичность свечи должна опираться на её
-- реальную метку времени, а не на временный индекс.
-- SQLite не умеет менять UNIQUE-констрейнт через ALTER TABLE, поэтому
-- таблица пересоздаётся.
CREATE TABLE candle_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    instrument_uid TEXT NOT NULL,
    time_index INTEGER NOT NULL,
    time INTEGER NOT NULL,
    open_price INTEGER NOT NULL,
    close_price INTEGER NOT NULL,
    high_price INTEGER NOT NULL,
    low_price INTEGER NOT NULL,
    volume INTEGER NOT NULL,
    volume_buy INTEGER NOT NULL DEFAULT 0,
    volume_sell INTEGER NOT NULL DEFAULT 0,
    UNIQUE(instrument_uid, time)
);

INSERT INTO candle_new (id, instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume, volume_buy, volume_sell)
SELECT id, instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume, volume_buy, volume_sell
FROM candle;

DROP TABLE candle;
ALTER TABLE candle_new RENAME TO candle;

CREATE INDEX IF NOT EXISTS idx_candle_instrument_time ON candle(instrument_uid, time);
CREATE INDEX IF NOT EXISTS idx_candle_time ON candle(time);
