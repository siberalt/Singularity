package com.siberalt.singularity.utils.entity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleIndexNormalizer;
import com.siberalt.singularity.entity.candle.CandleRangeMetadata;
import com.siberalt.singularity.entity.candle.MigrationCandleSource;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для миграции свечей из одного репозитория в другой.
 * Поддерживает параллельную обработку нескольких инструментов, а внутри
 * одного инструмента - параллельную обработку его чанков (см.
 * {@code chunkParallelism}).
 * <p>
 * Индекс свечей ({@code time_index}) чанки не проставляют сами - источник
 * может вернуть свечи в любом порядке относительно других чанков, поэтому
 * реальный индекс пересчитывается один раз после обработки всех чанков
 * инструмента через {@link CandleIndexNormalizer} (если {@code target} эту
 * способность поддерживает). Это и позволяет обрабатывать чанки одного
 * инструмента параллельно, не боясь гонки за общий счётчик.
 * <p>
 * Прогресс по уже перенесённым чанкам может сохраняться через
 * {@link CandleMigrationCheckpointRepository} - при повторном запуске
 * (например, после сбоя) уже перенесённые чанки не запрашиваются заново.
 */
public class CandleMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CandleMigrationService.class);

    private final MigrationCandleSource source;
    private final WriteCandleRepository target;
    private final ExecutorService executor;
    private final int chunkSizeDays; // размер временного чанка в днях
    private final int chunkParallelism; // количество параллельных потоков на чанки одного инструмента
    private final ProgressTrackerFactory progressTrackerFactory;
    private final CandleMigrationCheckpointRepository checkpoint;
    private final Object writeLock = new Object();

    /**
     * Единственный (канонический) конструктор - собирать сервис через {@link #builder}.
     *
     * @param source           репозиторий-источник (чтение)
     * @param target           репозиторий-приёмник (запись)
     * @param parallelism      количество параллельных потоков для обработки инструментов
     *                         (используется только в {@link #migrateInstruments})
     * @param chunkSizeDays    размер чанка в днях (разбиение временного интервала)
     * @param chunkParallelism количество параллельных потоков на чанки одного инструмента
     *                         (используется и в {@link #migrateInstrument}, и в {@link #migrateInstruments})
     */
    private CandleMigrationService(ProgressTrackerFactory progressTrackerFactory,
                                   MigrationCandleSource source,
                                   WriteCandleRepository target,
                                   int parallelism,
                                   int chunkSizeDays,
                                   CandleMigrationCheckpointRepository checkpoint,
                                   int chunkParallelism) {
        this.progressTrackerFactory = progressTrackerFactory;
        this.chunkSizeDays = chunkSizeDays;
        this.chunkParallelism = chunkParallelism;
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.target = target;
        this.source = source;
        this.checkpoint = checkpoint;
    }

    public static Builder builder(MigrationCandleSource source, WriteCandleRepository target) {
        return new Builder(source, target);
    }

    public static class Builder {
        private final MigrationCandleSource source;
        private final WriteCandleRepository target;
        private ProgressTrackerFactory progressTrackerFactory = new NullProgressTrackerFactory();
        private CandleMigrationCheckpointRepository checkpoint = new NoOpCandleMigrationCheckpointRepository();
        private int parallelism = 1;
        private int chunkSizeDays = 1;
        private int chunkParallelism = 1;

        private Builder(MigrationCandleSource source, WriteCandleRepository target) {
            this.source = source;
            this.target = target;
        }

        public Builder progressTrackerFactory(ProgressTrackerFactory progressTrackerFactory) {
            this.progressTrackerFactory = progressTrackerFactory;
            return this;
        }

        public Builder checkpoint(CandleMigrationCheckpointRepository checkpoint) {
            this.checkpoint = checkpoint;
            return this;
        }

        /**
         * Количество параллельных потоков для обработки инструментов (используется
         * только в {@link CandleMigrationService#migrateInstruments}).
         */
        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder chunkSizeDays(int chunkSizeDays) {
            this.chunkSizeDays = chunkSizeDays;
            return this;
        }

        /**
         * Количество параллельных потоков на чанки одного инструмента (используется
         * и в {@link CandleMigrationService#migrateInstrument}, и в {@link CandleMigrationService#migrateInstruments}).
         */
        public Builder chunkParallelism(int chunkParallelism) {
            this.chunkParallelism = chunkParallelism;
            return this;
        }

        public CandleMigrationService build() {
            return new CandleMigrationService(
                progressTrackerFactory, source, target, parallelism, chunkSizeDays, checkpoint, chunkParallelism
            );
        }
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

        // Предварительный расчёт всех чанков для всех инструментов (без учёта чекпойнта -
        // total должен отражать весь диапазон, а не только то, что осталось сделать)
        Map<String, List<MigrationChunk>> instrumentAllChunks = new HashMap<>();
        int totalChunks = 0;

        for (String instrumentUid : instrumentUids) {
            List<MigrationChunk> allChunks = computeChunks(instrumentUid, from, to);
            instrumentAllChunks.put(instrumentUid, allChunks);
            totalChunks += allChunks.size();
        }

        log.info("Total chunks to process: {}", totalChunks);

        ProgressTracker progressTracker = progressTrackerFactory.create(totalChunks);

        // Отсеиваем уже обработанные чанки и сразу продвигаем по ним прогресс - последовательно,
        // так как чекпойнт использует общее JDBC-соединение, не рассчитанное на конкурентный доступ
        Map<String, List<MigrationChunk>> instrumentPendingChunks = new HashMap<>();
        for (String instrumentUid : instrumentUids) {
            instrumentPendingChunks.put(
                instrumentUid,
                filterNotDone(instrumentUid, instrumentAllChunks.get(instrumentUid), progressTracker)
            );
        }

        List<CompletableFuture<Void>> futures = instrumentUids.stream()
                .map(uid -> CompletableFuture.runAsync(() -> {
                    List<MigrationChunk> chunks = instrumentPendingChunks.get(uid);
                    if (!chunks.isEmpty()) {
                        migrateInstrumentChunks(uid, chunks, progressTracker);
                    }
                }, executor))
                .toList();

        // Ожидаем завершения всех задач
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            shutdown(executor);
        }

        log.info("Migration completed for all instruments.");
    }

    /**
     * Миграция данных для одного инструмента в заданном интервале.
     * Интервал разбивается на чанки, которые обрабатываются параллельно
     * (см. {@code chunkParallelism}), после чего индексы свечей инструмента
     * пересчитываются одним шагом.
     *
     * @param instrumentUid идентификатор инструмента
     * @param from          начало интервала
     * @param to            конец интервала
     */
    public void migrateInstrument(String instrumentUid, Instant from, Instant to) {
        log.info("Processing instrument: {}", instrumentUid);
        List<MigrationChunk> allChunks = computeChunks(instrumentUid, from, to);
        ProgressTracker progressTracker = progressTrackerFactory.create(allChunks.size());
        List<MigrationChunk> pendingChunks = filterNotDone(instrumentUid, allChunks, progressTracker);
        migrateInstrumentChunks(instrumentUid, pendingChunks, progressTracker);
    }

    /**
     * Обрабатывает чанки одного инструмента параллельно (до {@code chunkParallelism}
     * одновременно) в отдельном, локальном для этого вызова пуле потоков. Чтение из
     * источника не синхронизировано (безопасно параллелить), а запись в {@code target}
     * и отметка чанка в чекпойнте - синхронизированы, так как оба используют разделяемое
     * JDBC-соединение, не рассчитанное на конкурентный доступ.
     * <p>
     * После обработки всех чанков, если {@code target} поддерживает
     * {@link CandleIndexNormalizer}, индексы свечей инструмента пересчитываются
     * начиная с самого раннего из обработанных в этом вызове чанков.
     */
    private void migrateInstrumentChunks(String instrumentUid, List<MigrationChunk> chunks, ProgressTracker progressTracker) {
        if (chunks.isEmpty()) {
            log.info("Nothing to migrate for instrument {}", instrumentUid);
            return;
        }

        AtomicInteger totalSaved = new AtomicInteger(0);
        ExecutorService chunkExecutor = Executors.newFixedThreadPool(Math.max(1, chunkParallelism));

        try {
            List<CompletableFuture<Void>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(
                    () -> processChunk(instrumentUid, chunk, progressTracker, totalSaved),
                    chunkExecutor
                ))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            shutdown(chunkExecutor);
        }

        if (target instanceof CandleIndexNormalizer normalizer) {
            Instant earliestProcessed = chunks.getFirst().from();
            log.debug("Normalizing indices for {} from {}", instrumentUid, earliestProcessed);
            normalizer.normalizeIndex(instrumentUid, earliestProcessed);
        }

        log.info("Finished instrument {}: total {} candles saved", instrumentUid, totalSaved.get());
    }

    private void processChunk(String instrumentUid, MigrationChunk chunk, ProgressTracker progressTracker, AtomicInteger totalSaved) {
        Instant chunkFrom = chunk.from();
        Instant chunkTo = chunk.to();

        List<Candle> candles;
        try {
            // Чтение свечей за чанк - не синхронизировано, безопасно выполнять параллельно.
            // Ошибки здесь ожидаемы (сеть, временная недоступность брокера) - чанк просто
            // не помечается обработанным и будет повторён при следующем запуске.
            candles = source.getPeriod(instrumentUid, chunkFrom, chunkTo);
        } catch (Exception e) {
            log.error("Error fetching chunk for {} from {} to {}", instrumentUid, chunkFrom, chunkTo, e);
            return;
        }

        // Ошибки записи (нарушение констрейнта, рассинхронизация схемы и т.п.) - это не
        // временная помеха, а признак бага, который повторится на каждом следующем чанке
        // точно так же. Такие исключения не глушим - пусть миграция упадёт целиком вместо
        // того, чтобы молча повторять одну и ту же ошибку.
        synchronized (writeLock) {
            if (!candles.isEmpty()) {
                target.saveBatch(candles);
                totalSaved.addAndGet(candles.size());
            }
            checkpoint.markDone(instrumentUid, chunk);
            progressTracker.advance(1);
        }

        log.debug("Saved {} candles for {} in chunk {} – {}", candles.size(), instrumentUid, chunkFrom, chunkTo);
    }

    /**
     * Разбивает реальный временной интервал на чанки фиксированного размера (в днях).
     * Сначала через getRangeMetadata получает фактические границы данных,
     * затем разбивает весь существующий диапазон на чанки, независимо от того,
     * что из них уже отмечено как обработанное в {@link #checkpoint} - это нужно,
     * чтобы total прогресс-трекера отражал весь диапазон, а не только его остаток.
     *
     * @param instrumentUid идентификатор инструмента
     * @param from начало запрошенного интервала
     * @param to   конец запрошенного интервала
     * @return полный список чанков в существующем диапазоне данных
     */
    private List<MigrationChunk> computeChunks(String instrumentUid, Instant from, Instant to) {
        CandleRangeMetadata metadata = source.getRangeMetadata(instrumentUid, from, to);
        if (metadata.isEmpty()) {
            log.debug("No candles found for {} in range {} – {}", instrumentUid, from, to);
            return List.of();
        }

        Instant actualFrom = metadata.range().fromTime();
        Instant actualTo = metadata.range().toTime();

        List<MigrationChunk> chunks = new ArrayList<>();
        Instant current = actualFrom;
        while (current.isBefore(actualTo)) {
            Instant next = current.plus(chunkSizeDays, ChronoUnit.DAYS);
            if (next.isAfter(actualTo)) {
                next = actualTo;
            }

            chunks.add(new MigrationChunk(current, next));
            current = next;
        }
        return chunks;
    }

    /**
     * Отсеивает чанки, уже отмеченные как обработанные в {@link #checkpoint}.
     * Для каждого уже сделанного чанка сразу продвигает {@code progressTracker} -
     * иначе он бы показывал прогресс только по чанкам, оставшимся на этот запуск,
     * а не по всему диапазону, для которого был посчитан total.
     * <p>
     * Не синхронизирован: вызывающий код обязан вызывать этот метод последовательно
     * для всех инструментов (чекпойнт использует общее JDBC-соединение, не рассчитанное
     * на конкурентный доступ).
     *
     * @return чанки, ещё не перенесённые ранее
     */
    private List<MigrationChunk> filterNotDone(String instrumentUid, List<MigrationChunk> allChunks, ProgressTracker progressTracker) {
        List<MigrationChunk> pending = new ArrayList<>();
        for (MigrationChunk chunk : allChunks) {
            if (checkpoint.isDone(instrumentUid, chunk)) {
                log.debug("Skipping already migrated chunk for {}: {} – {}", instrumentUid, chunk.from(), chunk.to());
                progressTracker.advance(1);
            } else {
                pending.add(chunk);
            }
        }
        return pending;
    }

    /**
     * Корректно завершает работу пула потоков.
     */
    private void shutdown(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
