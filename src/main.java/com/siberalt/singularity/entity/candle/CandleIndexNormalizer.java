package com.siberalt.singularity.entity.candle;

import java.time.Instant;

/**
 * Опциональная способность хранилища свечей пересчитать {@code time_index}
 * по хронологическому порядку. Нужна репозиториям, куда свечи могут
 * записываться не по порядку (например, параллельная миграция чанков) -
 * реализуют её только те хранилища, которым это применимо, а не
 * {@link WriteCandleRepository} целиком.
 */
public interface CandleIndexNormalizer {
    /**
     * Пересчитывает time_index для всех свечей инструмента с временем
     * {@code >= from} по возрастанию времени, продолжая нумерацию от
     * уже существующих (не затронутых) свечей до этой точки.
     *
     * @param instrumentUid идентификатор инструмента
     * @param from          начиная с какого момента времени пересчитывать индексы
     */
    void normalizeIndex(String instrumentUid, Instant from);
}
