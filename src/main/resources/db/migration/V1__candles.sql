-- Таблица свечей
CREATE TABLE IF NOT EXISTS candle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    instrument_uid TEXT NOT NULL,
    time_index INTEGER NOT NULL,        -- индекс временной точки (из TimePoint)
    time INTEGER NOT NULL,              -- время в миллисекундах с эпохи (Instant.toEpochMilli())
    open_price INTEGER NOT NULL,        -- цена = units * 1_000_000_000 + nano
    close_price INTEGER NOT NULL,
    high_price INTEGER NOT NULL,
    low_price INTEGER NOT NULL,
    volume INTEGER NOT NULL,
    UNIQUE(instrument_uid, time_index)  -- гарантирует уникальность свечи для инструмента и индекса
);

-- Индекс для быстрого поиска по инструменту и времени (диапазонные запросы)
CREATE INDEX IF NOT EXISTS idx_candle_instrument_time ON candle(instrument_uid, time);

-- Индекс для быстрого поиска по времени (если нужно)
CREATE INDEX IF NOT EXISTS idx_candle_time ON candle(time);
