package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Календарь по тому, что брокер уже объявил: {@link DividendCalendar} над дивидендами по бумагам.
 * <p>
 * Списки берутся готовыми, а не запрашиваются внутри: {@code nextLastBuyDate} зовут на каждом баре, и
 * сетевой вызов оттуда превратил бы прогон в опрос брокера. Так же сделана и таблица риск-ставок - брокера
 * спрашивают один раз, дальше читают таблицу.
 * <p>
 * Ключ - идентификатор бумаги в том виде, в каком она названа в свечах, а не брокерский uid. Сопоставление
 * одного с другим - дело того, кто собирает календарь: он и так знает, по какому набору бумаг идёт прогон.
 * <p>
 * Хранятся пары <i>(когда объявили, последний день покупки)</i>, отсортированные по дню покупки. Выбор
 * ближайшего - линейный проход по парам одной бумаги, потому что их у неё единицы в год; двоичным поиском
 * тут было бы нечего ускорять, а условие «объявлено к {@code asOf}» всё равно пришлось бы проверять подряд.
 *
 * <h2>Дивиденды без даты объявления выбрасываются</h2>
 * И это осознанный выбор из двух плохих. Включить их значит дать прогону знание, которого в тот момент не
 * было: правило вышло бы из позиции под отсечку, о которой ещё не объявили, и заработало бы на этом
 * процентные пункты из ничего. Выбросить значит потерять часть настоящих отсечек и получить оценку правила
 * <i>снизу</i>. Второе честнее: заниженный результат виден и обсуждаем, а подглядывание в будущее
 * выглядит как находка.
 * <p>
 * Сколько именно выброшено, говорит {@link #skipped()} - чтобы потеря была видна, а не молчалива.
 *
 * <h2>Бумага, которой календарь не знает</h2>
 * Про неё {@code nextLastBuyDate} отвечает «отсечек нет», и отличить это от «не платит дивидендов» изнутри
 * нельзя. Разница существенная: если ключи собраны не тем способом, которым названы свечи, правило молча не
 * сработает ни разу - а выглядеть это будет как «отсечки не мешали». Поэтому есть {@link #knows}: ею
 * вызывающий может проверить свой набор бумаг один раз на старте, вместо того чтобы разбираться потом.
 */
public class AnnouncedDividendCalendar implements DividendCalendar {
    private record Announced(Instant declaredAt, Instant lastBuyDate) {
    }

    private final Map<Long, List<Announced>> byInstrument;

    private final int skipped;

    private final int tooLate;

    public AnnouncedDividendCalendar(Map<Long, List<Dividend>> dividends) {
        if (dividends == null) {
            throw new IllegalArgumentException("Нет дивидендов, из которых собирать календарь");
        }

        Map<Long, List<Announced>> known = new HashMap<>();
        int lost = 0;
        int late = 0;

        for (Map.Entry<Long, List<Dividend>> instrument : dividends.entrySet()) {
            List<Announced> mine = new ArrayList<>();

            for (Dividend dividend : instrument.getValue()) {
                if (dividend.getDeclaredDate() == null || dividend.getLastBuyDate() == null) {
                    lost++;

                    continue;
                }

                if (DividendCalendar.dayOf(dividend.getDeclaredDate())
                    > DividendCalendar.dayOf(dividend.getLastBuyDate())) {
                    late++;
                }

                mine.add(new Announced(dividend.getDeclaredDate(), dividend.getLastBuyDate()));
            }

            mine.sort(Comparator.comparing(Announced::lastBuyDate));
            known.put(instrument.getKey(), List.copyOf(mine));
        }

        this.byInstrument = Map.copyOf(known);
        this.skipped = lost;
        this.tooLate = late;
    }

    /** Сколько дивидендов отброшено за отсутствие даты объявления или дня покупки. */
    public int skipped() {
        return skipped;
    }

    /**
     * Сколько дивидендов объявлены <b>не раньше</b> своего же дня покупки - то есть ни одно правило не
     * успело бы на них отреагировать.
     * <p>
     * Это не теоретическая оговорка, а измеренное свойство данных. У Сбера из четырёх дивидендов за
     * 2023-2026 один объявлен 2026-07-18 при дне покупки 2026-07-17, остальные - за 1, 9 и 19 дней.
     * Значит {@code declaredDate} у брокера не публичное объявление (его эмитент делает за месяцы), а
     * какая-то внутренняя дата фиксации, и опираться на неё можно только с коротким окном.
     * <p>
     * Отсюда ограничение, которое надо знать до того, как подбирать ширину окна: <b>окно нельзя расширять
     * сверх того, сколько даёт запас объявления</b>. Двухдневное окно эти данные держат в трёх случаях из
     * четырёх; десятидневное не сработало бы ни в одном из них, кроме июля 2024-го.
     * <p>
     * Такой дивиденд остаётся в календаре - он честно виден с даты объявления, просто поздно, - но счётчик
     * существует, чтобы «правило ни разу не сработало» не выглядело как «отсечек не было».
     */
    public int tooLate() {
        return tooLate;
    }

    /** Есть ли у календаря запись про эту бумагу - хоть пустая. См. замечание о неизвестной бумаге. */
    public boolean knows(long instrumentId) {
        return byInstrument.containsKey(instrumentId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Две даты сравниваются с разной точностью, и это не небрежность. <b>День покупки сравнивается по
     * дню</b>, потому что он и есть день: бар в десять утра того же дня обязан видеть его как наступивший,
     * а не как прошедший. <b>Дата объявления сравнивается по мгновению</b>, потому что знание приходит в
     * момент, и округление его вниз выдало бы правилу дивиденд на несколько часов раньше, чем о нём
     * объявили.
     */
    @Override
    public Instant nextLastBuyDate(long instrumentId, Instant asOf) {
        if (asOf == null) {
            return null;
        }

        long today = DividendCalendar.dayOf(asOf);

        for (Announced dividend : byInstrument.getOrDefault(instrumentId, List.of())) {
            if (DividendCalendar.dayOf(dividend.lastBuyDate()) < today
                || dividend.declaredAt().isAfter(asOf)) {
                continue;
            }

            return dividend.lastBuyDate();
        }

        return null;
    }
}
