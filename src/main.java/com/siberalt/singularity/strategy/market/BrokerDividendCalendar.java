package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.DividendInstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetDividendsRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Календарь прямо от брокера: {@link DividendInstrumentService} вместо заранее собранного снимка.
 * <p>
 * Снимок ({@link AnnouncedDividendCalendar}) годится для прогона по истории, где все отсечки периода
 * известны заранее и одинаковы от запуска к запуску. Живой стратегии он не годится по одной причине:
 * <b>объявления приходят со временем</b>. Снимок, снятый в январе, не знает о дивиденде, объявленном в
 * марте, и правило спокойно проедет отсечку, которой в нём нет.
 *
 * <h2>Кэш и почему он не просто кэш</h2>
 * {@code nextLastBuyDate} спрашивают на каждом баре, поэтому сетевой вызов оттуда превратил бы стратегию в
 * опрос брокера. Но и держать ответ вечно нельзя - см. выше. Отсюда два срока, и у каждого своя работа:
 * <ul>
 *   <li><b>{@code horizon}</b> - насколько вперёд спрашивать. Должен превышать самый долгий разрыв между
 *   дивидендами бумаги, иначе ближайшая отсечка окажется за краем запроса и правило её не увидит. По
 *   умолчанию {@value #DEFAULT_HORIZON_DAYS} дней: год с запасом, потому что годовой дивиденд приходит не
 *   ровно через год.</li>
 *   <li><b>{@code freshFor}</b> - сколько ответ считается свежим. Это и есть защита от пропущенного
 *   объявления: по умолчанию сутки, то есть раз в день брокера спрашивают заново, что для правила с
 *   точностью в день и есть нужная частота.</li>
 * </ul>
 * Свежесть меряется по тому же {@code asOf}, которым спрашивают, а не по системным часам: в прогоне по
 * истории время идёт по барам, и привязка к настоящим часам означала бы один запрос на весь прогон.
 * <p>
 * Запрос идёт не с самого {@code asOf}, а немного раньше: которой из своих дат брокер фильтрует диапазон -
 * объявления, отсечки или выплаты, - контракт не обещает, и дивиденд, чей день покупки вот-вот наступит,
 * мог бы отсечься краем. Запас {@value #DEFAULT_MARGIN_DAYS} дней назад стоит дешевле, чем пропущенная
 * отсечка.
 *
 * <h2>Отбор остаётся там, где был</h2>
 * Что считать известным и как сравнивать дни, решает {@link AnnouncedDividendCalendar}: ответ брокера
 * просто складывается в него. Второе место с теми же правилами разошлось бы с первым, а правила тут
 * нетривиальные - дата объявления сравнивается по мгновению, день покупки по дню.
 *
 * <h2>Отказ слышен</h2>
 * Ошибка брокера превращается в исключение, а не в «отсечек нет». Молчаливый пустой ответ здесь - худший из
 * возможных: правило продолжит держать позицию через отсечку и в логе это будет выглядеть как «дивидендов
 * не было». Поэтому каждый неудачный запрос падает громко, и это сознательно дороже в эксплуатации.
 * <p>
 * Экземпляр принадлежит одной стратегии и одному потоку - как и
 * {@link com.siberalt.singularity.strategy.signal.EntryExitSignalSource}, который его спрашивает.
 */
public class BrokerDividendCalendar implements DividendCalendar {
    public static final int DEFAULT_HORIZON_DAYS = 400;
    public static final int DEFAULT_MARGIN_DAYS = 30;
    public static final Duration DEFAULT_FRESH_FOR = Duration.ofDays(1);

    private record Fetched(Instant at, DividendCalendar calendar) {
    }

    private final DividendInstrumentService dividends;

    private final LongFunction<String> uidOf;

    private final Duration horizon;

    private final Duration margin;

    private final Duration freshFor;

    private final Map<Long, Fetched> cache = new HashMap<>();

    /**
     * @param dividends чем спрашивать брокера
     * @param uidOf     как из идентификатора бумаги в свечах получить брокерский uid; {@code null} в
     *                  ответе означает бумагу, которой у брокера нет
     * @param horizon   насколько вперёд спрашивать отсечки
     * @param freshFor  сколько ответ считается свежим, прежде чем спросить заново
     */
    public BrokerDividendCalendar(DividendInstrumentService dividends, LongFunction<String> uidOf,
                                  Duration horizon, Duration freshFor) {
        if (dividends == null || uidOf == null) {
            throw new IllegalArgumentException("Нужны служба дивидендов и способ узнать uid бумаги");
        }

        if (horizon == null || horizon.isNegative() || horizon.isZero()) {
            throw new IllegalArgumentException("Горизонт запроса должен быть положительным, получен "
                + horizon);
        }

        if (freshFor == null || freshFor.isNegative()) {
            throw new IllegalArgumentException("Срок свежести не может быть отрицательным, получен "
                + freshFor);
        }

        this.dividends = dividends;
        this.uidOf = uidOf;
        this.horizon = horizon;
        this.margin = Duration.ofDays(DEFAULT_MARGIN_DAYS);
        this.freshFor = freshFor;
    }

    public BrokerDividendCalendar(DividendInstrumentService dividends, LongFunction<String> uidOf) {
        this(dividends, uidOf, Duration.ofDays(DEFAULT_HORIZON_DAYS), DEFAULT_FRESH_FOR);
    }

    @Override
    public Instant nextLastBuyDate(long instrumentId, Instant asOf) {
        if (asOf == null) {
            return null;
        }

        Fetched fetched = cache.get(instrumentId);

        if (fetched == null || asOf.isBefore(fetched.at())
            || Duration.between(fetched.at(), asOf).compareTo(freshFor) > 0) {
            fetched = new Fetched(asOf, fetch(instrumentId, asOf));
            cache.put(instrumentId, fetched);
        }

        return fetched.calendar().nextLastBuyDate(instrumentId, asOf);
    }

    /** Что брокер знает про эту бумагу на этот момент, сложенное в календарь по объявленному. */
    private DividendCalendar fetch(long instrumentId, Instant asOf) {
        String uid = uidOf.apply(instrumentId);

        if (uid == null) {
            return DividendCalendar.EMPTY;
        }

        try {
            List<Dividend> paid = dividends.getDividends(
                GetDividendsRequest.of(uid, asOf.minus(margin), asOf.plus(horizon))).getDividends();

            return new AnnouncedDividendCalendar(Map.of(instrumentId, paid));
        } catch (AbstractException e) {
            // Пустой ответ здесь означал бы «отсечек нет», и правило проехало бы отсечку молча.
            throw new IllegalStateException(
                "Не удалось узнать дивиденды бумаги " + instrumentId + " на " + asOf, e);
        }
    }
}
