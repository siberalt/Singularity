package com.siberalt.singularity.entity.instrument;

import java.util.OptionalLong;

/**
 * Какой инструмент у нас скрывается за uid брокера.
 * <p>
 * Отдельный узкий контракт, а не метод {@link ReadInstrumentRepository}, потому что ответить на это
 * может не всякое хранилище листингов: {@link InMemoryInstrumentRepository} держит инструменты
 * брокера сами по себе и никаких наших идентификаторов не знает - добавь такой метод в общий
 * контракт, и одной из реализаций пришлось бы их выдумывать.
 * <p>
 * Нужен он тем, кто хранит данные против инструмента, а снаружи говорит на uid: свечам и чекпойнту
 * их миграции.
 */
@FunctionalInterface
public interface InstrumentIdResolver {
    OptionalLong idOf(String brokerInstrumentId);
}
