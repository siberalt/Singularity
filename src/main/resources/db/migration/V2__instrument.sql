-- Таблица инструментов (канонические данные, действительно не зависящие от брокера)
CREATE TABLE IF NOT EXISTS instrument (
    id INTEGER PRIMARY KEY AUTOINCREMENT,  -- внутренний instrumentId нашей системы
    isin TEXT UNIQUE,                      -- международный идентификатор ценной бумаги (может отсутствовать, напр. у фьючерсов)
    name TEXT NOT NULL,
    instrument_type TEXT NOT NULL          -- com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType
);

-- Листинг инструмента у конкретного брокера: идентификатор инструмента у брокера
-- плюс данные, зависящие от торговой площадки (размер лота и валюта расчётов
-- могут отличаться у разных брокеров даже для одного и того же ISIN)
CREATE TABLE IF NOT EXISTS instrument_broker_listing (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    instrument_id INTEGER NOT NULL REFERENCES instrument(id) ON DELETE CASCADE,
    broker_id TEXT NOT NULL,               -- ключ брокера, например "tinkoff", "mock"
    broker_instrument_id TEXT NOT NULL,    -- uid инструмента у брокера
    lot INTEGER NOT NULL,
    currency TEXT NOT NULL,
    UNIQUE(broker_id, broker_instrument_id),
    UNIQUE(broker_id, instrument_id)
);

-- Индекс для быстрого поиска всех листингов инструмента
CREATE INDEX IF NOT EXISTS idx_instrument_broker_listing_instrument_id ON instrument_broker_listing(instrument_id);
