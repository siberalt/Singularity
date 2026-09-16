package com.siberalt.singularity.utils.entity;

import java.util.List;

/**
 * Итог миграции одного инструмента.
 * <p>
 * Нужен потому, что упавший чанк не прерывает миграцию: сеть и брокер бывают недоступны, и такой
 * чанк просто не отмечается выполненным, чтобы его докачал следующий запуск. Без итога об этом
 * знал только лог - а когда логи никуда не пишутся, миграция с дырами выглядела ровно так же, как
 * полная.
 *
 * @param totalChunks    сколько чанков покрывает запрошенный интервал
 * @param skippedChunks  сколько из них уже были отмечены выполненными и не запрашивались
 * @param savedCandles   сколько свечей сохранено в этот запуск
 * @param failedChunks   чанки, которые не удалось получить, по возрастанию времени, - они остались
 *                       неотмеченными и будут повторены следующим запуском
 */
public record CandleMigrationResult(
    int totalChunks,
    int skippedChunks,
    long savedCandles,
    List<MigrationChunk> failedChunks
) {
    public CandleMigrationResult {
        failedChunks = List.copyOf(failedChunks);
    }

    public static final CandleMigrationResult EMPTY = new CandleMigrationResult(0, 0, 0, List.of());

    /** Весь интервал теперь отмечен выполненным: повторный запуск ничего не докачает. */
    public boolean isComplete() {
        return failedChunks.isEmpty();
    }
}
