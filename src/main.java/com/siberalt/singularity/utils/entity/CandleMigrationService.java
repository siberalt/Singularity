package com.siberalt.singularity.utils.entity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleRangeMetadata;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.WriteCandleRepository;
import com.siberalt.singularity.runtime.progress.NullProgressTrackerFactory;
import com.siberalt.singularity.runtime.progress.ProgressTracker;
import com.siberalt.singularity.runtime.progress.ProgressTrackerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для миграции свечей из одного репозитория в другой.
 * Поддерживает параллельную обработку нескольких инструментов,
 * а также пошаговую (чанками) загрузку данных для каждого инструмента.
 */
public class CandleMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CandleMigrationService.class);

    private final ReadCandleRepository source;
    private final WriteCandleRepository target;
    private final ExecutorService executor;
    private final int chunkSizeDays; // размер временного чанка в днях
    private final ProgressTrackerFactory progressTrackerFactory; // размер временного чанка в днях

    /**
     * Конструктор.
     *
     * @param source        репозиторий-источник (чтение)
     * @param target        репозиторий-приёмник (запись)
     * @param parallelism   количество параллельных потоков для обработки инструментов
     * @param chunkSizeDays размер чанка в днях (разбиение временного интервала)
     */
    public CandleMigrationService(ReadCandleRepository source,
                                  WriteCandleRepository target,
                                  int parallelism,
                                  int chunkSizeDays) {
        this.source = source;
        this.target = target;
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.chunkSizeDays = chunkSizeDays;
        this.progressTrackerFactory = new NullProgressTrackerFactory();
    }

    public CandleMigrationService(ProgressTrackerFactory progressTrackerFactory,
                                  ReadCandleRepository source,
                                  WriteCandleRepository target,
                                  int parallelism,
                                  int chunkSizeDays) {
        this.progressTrackerFactory = progressTrackerFactory;
        this.chunkSizeDays = chunkSizeDays;
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.target = target;
        this.source = source;
    }

    /**
     * Запускает миграцию для указанных инструментов в заданном временном интервале.
     * Перед запуском предварительно рассчитывает все чанки для всех инструментов
     * и создаёт общий ProgressTracker для отслеживания общего прогресса.
     *
     * @param instrumentUids список идентификаторов инструментов
     * @param from           начало интервала (включительно)
     * @param to             конец интервала (исключительно)
     */
    public void migrateInstruments(List<String> instrumentUids, Instant from, Instant to) {
        log.info("Starting migration for {} instruments from {} to {}", instrumentUids.size(), from, to);

        // Предварительный расчёт всех чанков для всех инструментов
        Map<String, List<Instant[]>> instrumentChunks = new HashMap<>();
        int totalChunks = 0;

        for (String instrumentUid : instrumentUids) {
            List<Instant[]> chunks = splitIntoChunks(instrumentUid, from, to);
            instrumentChunks.put(instrumentUid, chunks);
            totalChunks += chunks.size();
        }

        log.info("Total chunks to process: {}", totalChunks);

        ProgressTracker progressTracker = progressTrackerFactory.create(totalChunks);

        List<CompletableFuture<Void>> futures = instrumentUids.stream()
                .map(uid -> CompletableFuture.runAsync(() -> {
                    List<Instant[]> chunks = instrumentChunks.get(uid);
                    if (!chunks.isEmpty()) {
                        migrateInstrumentChunks(uid, chunks, progressTracker);
                    }
                }, executor))
                .toList();

        // Ожидаем завершения всех задач
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        shutdownExecutor();
        log.info("Migration completed for all instruments.");
    }

    /**
     * Миграция данных для одного инструмента в заданном интервале.
     * Интервал разбивается на чанки, которые обрабатываются последовательно,
     * чтобы избежать дублирования и избыточной нагрузки на репозиторий.
     *
     * @param instrumentUid идентификатор инструмента
     * @param from          начало интервала
     * @param to            конец интервала
     */
    public void migrateInstrument(String instrumentUid, Instant from, Instant to) {
        log.info("Processing instrument: {}", instrumentUid);
        List<Instant[]> chunks = splitIntoChunks(instrumentUid, from, to);
        ProgressTracker progressTracker = progressTrackerFactory.create(chunks.size());
        migrateInstrumentChunks(instrumentUid, chunks, progressTracker);
    }

    /**
     * Миграция данных для одного инструмента в заданном интервале.
     * Интервал разбивается на чанки, которые обрабатываются последовательно,
     * чтобы избежать дублирования и избыточной нагрузки на репозиторий.
     *
     * @param instrumentUid идентификатор инструмента
     */
    private void migrateInstrumentChunks(String instrumentUid, List<Instant[]> chunks, ProgressTracker progressTracker) {
        int totalSaved = 0;
        for (Instant[] chunk : chunks) {
            Instant chunkFrom = chunk[0];
            Instant chunkTo = chunk[1];
            try {
                // Чтение свечей за чанк
                List<Candle> candles = source.getPeriod(instrumentUid, chunkFrom, chunkTo);
                if (candles.isEmpty()) {
                    log.debug("No candles for {} in chunk {} – {}", instrumentUid, chunkFrom, chunkTo);
                    continue;
                }

                target.saveBatch(candles);

                totalSaved += candles.size();
                progressTracker.advance(1);
                log.debug("Saved {} candles for {} in chunk {} – {}", candles.size(), instrumentUid, chunkFrom, chunkTo);
            } catch (Exception e) {
                log.error("Error migrating chunk for {} from {} to {}", instrumentUid, chunkFrom, chunkTo, e);
                // Можно добавить логику повторных попыток (retry) или пропуск проблемного чанка
            }
        }

        log.info("Finished instrument {}: total {} candles saved", instrumentUid, totalSaved);
    }

    /**
     * Разбивает реальный временной интервал на чанки фиксированного размера (в днях).
     * Сначала через getRangeMetadata получает фактические границы данных,
     * затем разбивает только существующий диапазон на чанки.
     *
     * @param instrumentUid идентификатор инструмента
     * @param from начало запрошенного интервала
     * @param to   конец запрошенного интервала
     * @return список пар {начало, конец} для каждого чанка с данными
     */
    private List<Instant[]> splitIntoChunks(String instrumentUid, Instant from, Instant to) {
        CandleRangeMetadata metadata = source.getRangeMetadata(instrumentUid, from, to);
        if (metadata.isEmpty()) {
            log.debug("No candles found for {} in range {} – {}", instrumentUid, from, to);
            return List.of();
        }

        Instant actualFrom = metadata.range().fromTime();
        Instant actualTo = metadata.range().toTime();

        List<Instant[]> chunks = new ArrayList<>();
        Instant current = actualFrom;
        while (current.isBefore(actualTo)) {
            Instant next = current.plus(chunkSizeDays, ChronoUnit.DAYS);
            if (next.isAfter(actualTo)) {
                next = actualTo;
            }
            chunks.add(new Instant[]{current, next});
            current = next;
        }
        return chunks;
    }

    /**
     * Корректно завершает работу пула потоков.
     */
    private void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
