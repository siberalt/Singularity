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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Сколько раз пауза перед повтором успевает удвоиться, прежде чем перестаёт расти. */
    private static final int MAX_DOUBLINGS = 3;

    private final MigrationCandleSource source;
    private final WriteCandleRepository target;
    private final ExecutorService executor;
    private final int chunkSizeDays; // размер временного чанка в днях
    private final int chunkParallelism; // количество параллельных потоков на чанки одного инструмента
    private final ProgressTrackerFactory progressTrackerFactory;
    private final CandleMigrationCheckpointRepository checkpoint;
    private final int retries; // сколько раз повторять чанк, не получившийся с первого раза
    private final Duration retryBackoff; // пауза перед первым повтором; каждая следующая вдвое длиннее
    // Лимит запросов принадлежит источнику, а не инструменту: одна пауза на сервис, а значит и на все
    // инструменты, которые он переносит через один и тот же клиент.
    private final Pause pause = new Pause();
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
                                   int chunkParallelism,
                                   int retries,
                                   Duration retryBackoff) {
        this.retries = retries;
        this.retryBackoff = retryBackoff;
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
        private int retries;
        private Duration retryBackoff = Duration.ofSeconds(30);

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

        /**
         * Сколько раз повторять чанк, который не получился с первого раза. Ноль, по умолчанию, -
         * не повторять вовсе: чанк просто не помечается обработанным и достаётся следующему запуску.
         */
        public Builder retries(int retries) {
            if (retries < 0) {
                throw new IllegalArgumentException("Повторов не может быть меньше нуля, получено " + retries);
            }

            this.retries = retries;
            return this;
        }

        /**
         * Пауза перед первым повтором; каждая следующая вдвое длиннее. Пауза общая на инструмент -
         * лимит запросов выдаётся на окно времени, и ждать его восстановления всем потокам сразу
         * дешевле, чем каждому по отдельности.
         */
        public Builder retryBackoff(Duration retryBackoff) {
            if (retryBackoff.isNegative()) {
                throw new IllegalArgumentException("Пауза не может быть отрицательной, получено " + retryBackoff);
            }

            this.retryBackoff = retryBackoff;
            return this;
        }

        public CandleMigrationService build() {
            return new CandleMigrationService(
                progressTrackerFactory, source, target, parallelism, chunkSizeDays, checkpoint, chunkParallelism,
                retries, retryBackoff
            );
        }
    }

    /**
     * Запускает миграцию для указанных инструментов в заданном временном интервале.
     * Перед запуском предварительно рассчитывает все чанки для всех инструментов
     * и создаёт общий ProgressTracker для отслеживания общего прогресса.
     *
     * @param instrumentIds  список идентификаторов инструментов
     * @param from           начало интервала (включительно)
     * @param to             конец интервала (исключительно)
     * @return итог миграции каждого инструмента, в том числе чанки, которые не удалось получить
     */
    public Map<Long, CandleMigrationResult> migrateInstruments(List<Long> instrumentIds, Instant from, Instant to) {
        log.info("Starting migration for {} instruments from {} to {}", instrumentIds.size(), from, to);

        // Предварительный расчёт всех чанков для всех инструментов (без учёта чекпойнта -
        // total должен отражать весь диапазон, а не только то, что осталось сделать)
        Map<Long, List<MigrationChunk>> instrumentAllChunks = new HashMap<>();
        int totalChunks = 0;

        for (long instrumentId : instrumentIds) {
            List<MigrationChunk> allChunks = computeChunks(instrumentId, from, to);
            instrumentAllChunks.put(instrumentId, allChunks);
            totalChunks += allChunks.size();
        }

        log.info("Total chunks to process: {}", totalChunks);

        ProgressTracker progressTracker = progressTrackerFactory.create(totalChunks);

        // Отсеиваем уже обработанные чанки и сразу продвигаем по ним прогресс - последовательно,
        // так как чекпойнт использует общее JDBC-соединение, не рассчитанное на конкурентный доступ
        Map<Long, List<MigrationChunk>> instrumentPendingChunks = new HashMap<>();
        for (long instrumentId : instrumentIds) {
            instrumentPendingChunks.put(
                instrumentId,
                filterNotDone(instrumentId, instrumentAllChunks.get(instrumentId), progressTracker)
            );
        }

        Map<Long, CandleMigrationResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = instrumentIds.stream()
                .map(uid -> CompletableFuture.runAsync(() -> {
                    List<MigrationChunk> all = instrumentAllChunks.get(uid);
                    List<MigrationChunk> chunks = instrumentPendingChunks.get(uid);
                    results.put(uid, migrateInstrumentChunks(uid, all.size(), chunks, progressTracker));
                }, executor))
                .toList();

        // Ожидаем завершения всех задач
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            shutdown(executor);
        }

        log.info("Migration completed for all instruments.");

        return results;
    }

    /**
     * Миграция данных для одного инструмента в заданном интервале.
     * Интервал разбивается на чанки, которые обрабатываются параллельно
     * (см. {@code chunkParallelism}), после чего индексы свечей инструмента
     * пересчитываются одним шагом.
     *
     * @param instrumentId идентификатор инструмента
     * @param from          начало интервала
     * @param to            конец интервала
     * @return итог миграции, в том числе чанки, которые не удалось получить и которые докачает
     *         следующий запуск
     */
    public CandleMigrationResult migrateInstrument(long instrumentId, Instant from, Instant to) {
        log.info("Processing instrument: {}", instrumentId);
        List<MigrationChunk> allChunks = computeChunks(instrumentId, from, to);
        ProgressTracker progressTracker = progressTrackerFactory.create(allChunks.size());
        List<MigrationChunk> pendingChunks = filterNotDone(instrumentId, allChunks, progressTracker);
        return migrateInstrumentChunks(instrumentId, allChunks.size(), pendingChunks, progressTracker);
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
    private CandleMigrationResult migrateInstrumentChunks(
        long instrumentId,
        int totalChunks,
        List<MigrationChunk> chunks,
        ProgressTracker progressTracker
    ) {
        int skippedChunks = totalChunks - chunks.size();

        if (chunks.isEmpty()) {
            log.info("Nothing to migrate for instrument {}", instrumentId);
            return new CandleMigrationResult(totalChunks, skippedChunks, 0, List.of());
        }

        AtomicInteger totalSaved = new AtomicInteger(0);
        Queue<MigrationChunk> failed = new ConcurrentLinkedQueue<>();
        ExecutorService chunkExecutor = Executors.newFixedThreadPool(Math.max(1, chunkParallelism));

        try {
            List<CompletableFuture<Void>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(
                    () -> processChunk(instrumentId, chunk, progressTracker, totalSaved, failed),
                    chunkExecutor
                ))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            shutdown(chunkExecutor);
        }

        if (target instanceof CandleIndexNormalizer normalizer) {
            Instant earliestProcessed = chunks.getFirst().from();
            log.debug("Normalizing indices for {} from {}", instrumentId, earliestProcessed);
            normalizer.normalizeIndex(instrumentId, earliestProcessed);
        }

        log.info("Finished instrument {}: total {} candles saved, {} chunks failed", instrumentId, totalSaved.get(), failed.size());

        List<MigrationChunk> failedInOrder = failed.stream()
            .sorted(Comparator.comparing(MigrationChunk::from))
            .toList();

        return new CandleMigrationResult(totalChunks, skippedChunks, totalSaved.get(), failedInOrder);
    }

    private void processChunk(
        long instrumentId,
        MigrationChunk chunk,
        ProgressTracker progressTracker,
        AtomicInteger totalSaved,
        Queue<MigrationChunk> failed
    ) {
        Instant chunkFrom = chunk.from();
        Instant chunkTo = chunk.to();

        List<Candle> candles = fetch(instrumentId, chunk);

        if (candles == null) {
            failed.add(chunk);
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
            checkpoint.markDone(instrumentId, chunk);
            progressTracker.advance(1);
        }

        log.debug("Saved {} candles for {} in chunk {} – {}", candles.size(), instrumentId, chunkFrom, chunkTo);
    }

    /**
     * Свечи чанка, с повторами. {@code null} - не получилось и после них, чанк остаётся на следующий
     * запуск.
     * <p>
     * Ошибки здесь ожидаемы и бывают двух родов. Сеть до брокера пропадает на секунды, и такой чанк
     * берётся со второй попытки. Но чаще упирается лимит запросов: он выдаётся на окно времени, и когда
     * он исчерпан, падают разом все чанки, которые в этот момент в работе. Повторять их немедленно
     * бессмысленно - лимит от этого не восстановится, - поэтому пауза общая: наткнувшийся на отказ
     * поток отодвигает её для всех, и остальные ждут вместе с ним, вместо того чтобы по очереди
     * тратить попытки в закрытое окно.
     * <p>
     * Ждём перед попыткой, а не после отказа. Чанк, который только подошёл к очереди, о закрытом окне
     * не знает, и если не спросить паузу заранее, он потратит попытку впустую - а попыток у него
     * столько же, сколько у остальных.
     */
    private List<Candle> fetch(long instrumentId, MigrationChunk chunk) {
        for (int attempt = 0; ; attempt++) {
            try {
                pause.await();

                // Чтение свечей за чанк - не синхронизировано, безопасно выполнять параллельно.
                return source.getPeriod(instrumentId, chunk.from(), chunk.to());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while fetching chunk for {} from {}", instrumentId, chunk.from());

                return null;
            } catch (Exception e) {
                if (attempt >= retries) {
                    log.error("Error fetching chunk for {} from {} to {}, giving up after {} attempts",
                        instrumentId, chunk.from(), chunk.to(), attempt + 1, e);

                    return null;
                }

                // Каждая следующая пауза вдвое длиннее: короткой хватает на моргнувшую сеть, длинная
                // нужна, чтобы дождаться нового окна лимита. Дальше восьмикратной расти незачем - окно
                // лимита конечно, и ждать дольше него значит просто простаивать.
                Duration wait = retryBackoff.multipliedBy(1L << Math.min(attempt, MAX_DOUBLINGS));

                log.warn("Error fetching chunk for {} from {}, attempt {} of {}, waiting {}",
                    instrumentId, chunk.from(), attempt + 1, retries + 1, wait, e);
                pause.hold(wait);
            }
        }
    }

    /**
     * Запрет ходить к источнику до определённого момента, общий для всех, кто через этот источник
     * ходит. Поток, получивший отказ, отодвигает момент, остальные ждут его перед своей попыткой.
     */
    static class Pause {
        /**
         * Момент, до которого никто не ходит к источнику. Именно момент, а не остаток: сроки задают
         * разные потоки в разное время, и складывать их длительности значило бы ждать сумму пауз
         * вместо самой поздней из них.
         */
        private final AtomicLong until = new AtomicLong();

        void hold(Duration wait) {
            until.accumulateAndGet(System.currentTimeMillis() + wait.toMillis(), Math::max);
        }

        void await() throws InterruptedException {
            long wait = until.get() - System.currentTimeMillis();

            if (wait > 0) {
                Thread.sleep(wait);
            }
        }
    }

    /**
     * Разбивает реальный временной интервал на чанки фиксированного размера (в днях).
     * Сначала через getRangeMetadata получает фактические границы данных,
     * затем разбивает весь существующий диапазон на чанки, независимо от того,
     * что из них уже отмечено как обработанное в {@link #checkpoint} - это нужно,
     * чтобы total прогресс-трекера отражал весь диапазон, а не только его остаток.
     *
     * @param instrumentId идентификатор инструмента
     * @param from начало запрошенного интервала
     * @param to   конец запрошенного интервала
     * @return полный список чанков в существующем диапазоне данных
     */
    private List<MigrationChunk> computeChunks(long instrumentId, Instant from, Instant to) {
        CandleRangeMetadata metadata = source.getRangeMetadata(instrumentId, from, to);
        if (metadata.isEmpty()) {
            log.debug("No candles found for {} in range {} – {}", instrumentId, from, to);
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
    private List<MigrationChunk> filterNotDone(long instrumentId, List<MigrationChunk> allChunks, ProgressTracker progressTracker) {
        List<MigrationChunk> pending = new ArrayList<>();
        for (MigrationChunk chunk : allChunks) {
            if (checkpoint.isDone(instrumentId, chunk)) {
                log.debug("Skipping already migrated chunk for {}: {} – {}", instrumentId, chunk.from(), chunk.to());
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
