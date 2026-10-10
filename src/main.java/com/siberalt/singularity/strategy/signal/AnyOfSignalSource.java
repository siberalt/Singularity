package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Первый, кто скажет хоть что-то, и отвечает. Способ сложить несколько источников, не усредняя их.
 * <p>
 * Чем это не {@link CompositeFactorSignalSource}: тот взвешивает мнения и выдаёт их смесь, и для мнений
 * это правильно - пять факторов о тренде складываются в один взгляд. А для <b>распоряжений</b> смесь
 * бессмысленна: половина стопа не стоп. Выходов у правила бывает несколько - свой сигнал, срок под
 * дивидендную отсечку, конец сессии, - и сработать должен тот, который сработал, а не их среднее.
 * <p>
 * <b>Порядок значит приоритет</b>, и это единственная настройка. Спрашивают по порядку и останавливаются
 * на первом ответе; значит обязательный выход ставят раньше условного. Делегаты за сработавшим не
 * спрашиваются вовсе - полезно, когда дальше в списке стоит что-то дорогое, и важно помнить, если дальше
 * стоит что-то, считающее бары
 * ({@link FixedReverserSignalSource}, {@link EntryExitSignalSource} меряют удержание вызовами).
 * <p>
 * <b>Что считается ответом.</b> Либо уверенность не ноль, либо тип закрывающий. Посчитанный ноль -
 * «ни туда ни сюда» - ответом не считается и список не обрывает: он никуда не указывает и ничего не
 * велит, так что следующему источнику есть что сказать. Это то же различие, которое ввёл
 * {@link SignalType}: молчание и ноль - не одно и то же, но ни одно из них не является распоряжением.
 */
public class AnyOfSignalSource implements SignalSource {
    private final List<SignalSource> sources;

    /**
     * @param sources по порядку приоритета: обязательные выходы раньше условных
     */
    public AnyOfSignalSource(List<SignalSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Нечего спрашивать: список источников пуст");
        }

        // Обходом, а не contains(null): неизменяемый список из List.of на такой запрос бросает NPE сам,
        // и проверка, поставленная ради внятного сообщения, давала бы наименее внятное из возможных.
        for (SignalSource source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("Источник в списке не может быть пустым");
            }
        }

        this.sources = List.copyOf(sources);
    }

    public AnyOfSignalSource(SignalSource... sources) {
        this(List.of(sources));
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        for (SignalSource source : sources) {
            Signal reading = source.calculate(lastCandles);

            if (reading.confidence() != 0 || reading.type().closes()) {
                return reading;
            }
        }

        return Signal.NEUTRAL;
    }
}
