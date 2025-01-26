package com.siberalt.singularity.broker.contract.service.exception;

public enum ErrorCode {
    UNIMPLEMENTED(ErrorType.UNIMPLEMENTED), //Method is unimplemented	Метод не реализован.
    UNAVAILABLE(ErrorType.UNAVAILABLE), //Deprecated method is unavailable	Метод устарел и недоступен.
    UNKNOWN(ErrorType.UNKNOWN),
    MISSING_PARAMETER_FROM(ErrorType.INVALID_REQUEST), //Missing parameter: from	Входной параметр from является обязательным.
    INVALID_PARAMETER_FROM(ErrorType.INVALID_REQUEST), //Missing parameter: from	Входной параметр from является обязательным.
    PERIOD_WEEK_EXCEED(ErrorType.INVALID_REQUEST), //The required period should not exceed 7 days	Запрошенный период не может превышать 7 дней.
    FROM_LESS_THAN_CURRENT_DATE(ErrorType.INVALID_REQUEST), //from can't be less than the current date	Входной параметр from не может быть меньше текущей даты.
    MISSING_PARAMETER_TO(ErrorType.INVALID_REQUEST), //Missing parameter: to	Входной параметр to является обязательным.
    MISSING_PARAMETER_ID(ErrorType.INVALID_REQUEST), //Missing parameter: id	Входной параметр id является обязательным.
    INVALID_PARAMETER_TO(ErrorType.INVALID_REQUEST), //to is invalid	Входной параметр to имеет некорректное значение.
    INVALID_INTERVAL(ErrorType.INVALID_REQUEST), //interval is invalid	Входной параметр interval имеет некорректное значение.
    TO_LESS_THAN_FROM(ErrorType.INVALID_REQUEST), //to can't be less than from	Входной параметр to не может быть меньше параметра from.
    CANDLE_PERIOD_EXCEED(ErrorType.INVALID_REQUEST), //The maximum request period for the given candle interval has been exceeded	Превышен максимальный период запроса для данного интервала свечи.
    MISSING_PARAMETER_QUANTITY(ErrorType.INVALID_REQUEST), //Parameter quantity is missing or equal to 0	Входной параметр quantity является обязательным.
    INVALID_PARAMETER_QUANTITY(ErrorType.INVALID_REQUEST), //quantity is invalid	Входной параметр quantity имеет некорректное значение.
    MISSING_PARAMETER_PRICE(ErrorType.INVALID_REQUEST), //Missing parameter: price	Входной параметр price является обязательным.
    INVALID_PARAMETER_PRICE(ErrorType.INVALID_REQUEST), //price is invalid	Входной параметр price имеет некорректное значение.
    MISSING_PARAMETER_DIRECTION(ErrorType.INVALID_REQUEST), //Missing parameter: direction	Входной параметр direction является обязательным.
    INVALID_PARAMETER_DIRECTION(ErrorType.INVALID_REQUEST), //direction is invalid	Входной параметр direction имеет некорректное значение.
    MISSING_PARAMETER_ACCOUNT_ID(ErrorType.INVALID_REQUEST), //Missing parameter: account_id	Входной параметр account_id является обязательным.
    INVALID_PARAMETER_ACCOUNT_ID(ErrorType.INVALID_REQUEST), //account_id is invalid	Входной параметр account_id имеет некорректное значение.
    INVALID_PARAMETER_STATE(ErrorType.INVALID_REQUEST), //Invalid parameter: state	Входной параметр state имеет некорректное значение.
    MISSING_PARAMETER_ORDER_TYPE(ErrorType.INVALID_REQUEST), //Missing parameter: order_type	Входной параметр order_type является обязательным.
    INVALID_PARAMETER_ORDER_TYPE(ErrorType.INVALID_REQUEST), //Invalid parameter: order_type	Входной параметр order_type имеет некорректное значение.
    MISSING_PARAMETER_ORDER_ID(ErrorType.INVALID_REQUEST), //Missing parameter: order_id	Входной параметр order_id является обязательным.
    INVALID_PARAMETER_ORDER_ID(ErrorType.INVALID_REQUEST), //order id has invalid UUID format	Входной параметр order_id имеет некорректное значение.
    MISSING_PARAMETER_IDEMPOTENCY_KEY(ErrorType.INVALID_REQUEST), //Missing parameter: idempotency_key	Входной параметр idempotency_key является обязательным.
    INVALID_PARAMETER_IDEMPOTENCY_KEY(ErrorType.INVALID_REQUEST), //idempotency_key is invalid	Входной параметр idempotency_key имеет некорректное значение.
    MISSING_PARAMETER_DEPTH(ErrorType.INVALID_REQUEST), //Missing parameter: depth	Входной параметр depth является обязательным.
    INVALID_PARAMETER_DEPTH(ErrorType.INVALID_REQUEST), //depth is invalid	Входной параметр depth имеет некорректное значение.
    INSUFFICIENT_BALANCE(ErrorType.INVALID_REQUEST), //Not enough balance	Недостаточно средств для совершения сделки (ошибка песочницы).
    MISSING_PARAMETER_STOP_PRICE(ErrorType.INVALID_REQUEST), //Missing parameter: stop_price	Входной параметр stop_price является обязательным.
    MISSING_PARAMETER_STOP_ORDER_TYPE(ErrorType.INVALID_REQUEST), //Missing parameter: stop_order_type	Входной параметр stop_order_type является обязательным.
    INVALID_PARAMETER_STOP_ORDER_TYPE(ErrorType.INVALID_REQUEST), //stop_order_type is invalid	Входной параметр stop_order_type имеет некорректное значение.
    INVALID_EXPIRE_DATE(ErrorType.INVALID_REQUEST), //expire_date is invalid	Входной параметр expire_date имеет некорректное значение.
    INSTRUMENT_IS_NOT_BOND(ErrorType.INVALID_REQUEST), //Instrument type is not bond	Метод предназначен только для запроса информации по облигации.
    POST_ORDER_ERROR(ErrorType.INVALID_REQUEST), //Post order error: %s	Ошибка метода выставления торгового поручения.
    INVALID_INSTRUMENT_STATUS(ErrorType.INVALID_REQUEST), //instrument_status is invalid	Входной параметр instrument_status имеет некорректное значение.
    POST_STOP_ORDER_ERROR(ErrorType.INVALID_REQUEST), //Post stop_order error: %s	Ошибка метода выставления стоп-заявки.
    INSTRUMENT_IS_NOT_SHARE(ErrorType.INVALID_REQUEST), //Instrument type is not a share or etf	Тип инструмента не инвестиционный фонд или акция
    TOO_LONG_ORDER_ID(ErrorType.INVALID_REQUEST), //order_id cannot be longer than 36 characters	order_id не может быть длиннее 36 символов
    STOP_ORDER_CURRENCY_UNSUPPORTED(ErrorType.INVALID_REQUEST), //Stop order settlement currency is not supported	Валюта выставления стоп-заявки не поддерживается
    DUPLICATE_ORDER(ErrorType.INVALID_REQUEST), //The order is a duplicate, but the order report was not found	Заявка является дублем, но отчет по заявке не найден.
    TASK_STILL_RUNNING(ErrorType.INVALID_REQUEST), //Task not completed yet, please try again later	Выполнение задачи еще не завершено, попробуйте позже.
    CANCEL_ORDER_ERROR(ErrorType.INVALID_REQUEST), //Cancel order error: %s	Ошибка метода отмены заявки.
    CANCEL_STOP_ORDER_ERROR(ErrorType.INVALID_REQUEST), //Cancel stop-order error: %s	Ошибка метода отмены стоп-заявки.
    PARAMETER_FROM_OUT_OF_RANGE(ErrorType.INVALID_REQUEST), //from value out of range	Входной параметр from имеет некорректное значение.
    PARAMETER_TO_OUT_OF_RANGE(ErrorType.INVALID_REQUEST), //to value out of range	Входной параметр to имеет некорректное значение.
    PARAMETER_EXPIRE_DATE_OUT_OF_RANGE(ErrorType.INVALID_REQUEST), //expire_date value out of range	Входной параметр expire_date имеет некорректное значение.
    PERIOD_MONTH_EXCEED(ErrorType.INVALID_REQUEST), //The required period should not exceed 31 days	Запрошенный период не может превышать 31 дня.
    MISSING_PARAMETER_TASK_ID(ErrorType.INVALID_REQUEST), //Missing parameter: task_id	Входной параметр task_id является обязательным.
    MISSING_PARAMETER_PAYLOAD(ErrorType.INVALID_REQUEST), //Missing parameter: payload	Входной параметр payload является обязательным.
    INVALID_ACTION_TYPE(ErrorType.INVALID_REQUEST), //action_type is invalid	Некорректное значение action_type.
    ONLY_LIMIT_ORDER_ALLOWED(ErrorType.INVALID_REQUEST), //Only limit order is allowed	В настоящий момент возможно выставление только лимитного торгового поручения. Подробнее про выставление торговых поручений.
    INVALID_PARAMETER_LIMIT(ErrorType.INVALID_REQUEST), //Invalid parameter: limit	Входной параметр limit имеет некорректное значение.
    FROM_MORE_THAN_CURRENT_DATE(ErrorType.INVALID_REQUEST), //from can't be more than the current date	Входной параметр from имеет некорректное значение.
    INVALID_MINIMUM_PRICE_INCREMENT(ErrorType.INVALID_REQUEST), //Incorrect minimum price increment	Некорректный шаг изменения цены.
    INSTRUMENT_UNAVAILABLE_FOR_TRADING(ErrorType.INVALID_REQUEST), //Instrument is not available for trading	Инструмент недоступен для торгов. Подробнее о торговых статусах.
    QUANTITY_MUST_BE_POSITIVE(ErrorType.INVALID_REQUEST), //quantity must be positive	Количество лотов должно быть положительным числом.
    ACCOUNT_CLOSED(ErrorType.INVALID_REQUEST), //Account status is closed	Аккаунт закрыт.
    ACCOUNT_BLOCKED(ErrorType.INVALID_REQUEST), //Account status is blocked	Аккаунт заблокирован.
    PERIOD_MAX_LIMIT_REQUEST_EXCEED(ErrorType.INVALID_REQUEST), //Maximum request period has been exceeded	Превышен лимит запрашиваемого периода.
    INVALID_YEAR(ErrorType.INVALID_REQUEST), //Year is invalid	Некорректный год.
    MISSING_PARAMETER_QUERY(ErrorType.INVALID_REQUEST), //Missing parameter: query	Входной параметр query является обязательным.
    FROM_AND_TO_MUST_HAVE_SAME_YEAR(ErrorType.INVALID_REQUEST), //from and to must have the same year	Запрашиваемые даты должны быть в рамках одного года.
    TO_MUST_NOT_BE_LATER_THAN(ErrorType.INVALID_REQUEST), //to must not be later than %s	Поле to не должно быть позднее даты, указанной в тексте ошибки.
    TRADING_UNAVAILABLE_ON_WEEKENDS(ErrorType.INVALID_REQUEST), //Trading unavailable on weekends	Торги недоступны по нерабочим дням. Подробнее о торговых сессиях.
    MISSING_PARAMETER_INSTRUMENT_ID(ErrorType.INVALID_REQUEST), //Missing parameter instrument_id	Один из параметров figi или instrument_id является обязательным.
    REQUEST_WAS_NOT_EXECUTED(ErrorType.INVALID_REQUEST), //The request was not executed by the exchange	Заявка не исполнена биржей.
    ORDER_IS_REJECTED(ErrorType.INVALID_REQUEST), //The order was rejected, try again later	Заявка отклонена, попробуйте повторить позже.
    INAPPROPRIATE_TRADING_SESSION(ErrorType.INVALID_REQUEST), //Inappropriate trading session	Сейчас эта сессия не идёт. Подробнее о торговых сессиях.
    INSTRUMENT_PRICE_IS_OUT_OF_LIMITS(ErrorType.INVALID_REQUEST), //The price is outside the limits for this instrument	Цена вне лимитов по инструменту или цена сделки вне лимита. Подробнее про выставление торговых поручений.
    PRICE_MUST_BE_POSITIVE(ErrorType.INVALID_REQUEST), //The price must be positive	Цена должна быть положительной.
    ONLY_BEST_PRICE_IS_ALLOWED(ErrorType.INVALID_REQUEST), //Only best price is allowed	Для инструмента можно выставить заявки только с типом «лучшая цена».
    INVALID_PRICE_TYPE(ErrorType.INVALID_REQUEST), //price_type is invalid	Некорректное значение price_type. Значением price_type может быть только PRICE_TYPE_POINT или PRICE_TYPE_CURRENCY.
    INVALID_STATUS(ErrorType.INVALID_REQUEST), //status is invalid	Некорректное значение status. Значением status может быть только ACTIVE, EXCECUTED, CANCELED И EXPIRED.
    INVALID_PAGINATION_PARAMETERS(ErrorType.INVALID_REQUEST), //Negative values are not allowed	Некорректное значение limit и/или page. Параметры limit и page не могут принимать отицательные значения.
    MAXIMUM_TRANSACTION_AMOUNT_HAS_BEEN_EXCEEDED(ErrorType.INVALID_REQUEST), //Maximum transaction amount has been exceeded	Превышена максимальная сумма сделки. Разделите ордер на несколько ордеров меньшего объема.
    MISSING_PARAMETER_INDENT_TYPE(ErrorType.INVALID_REQUEST), //indent type required	Проверьте параметры запроса стоп-заявки. Не задан indent_type.
    MISSING_PARAMETER_SPREAD_TYPE(ErrorType.INVALID_REQUEST), //spread type required	Проверьте параметры запроса стоп-заявки. Не задан spread_type.
    INVALID_INDICATOR_TYPE(ErrorType.INVALID_REQUEST), //indicator_type is invalid	Проверьте параметры запроса. Некорректный indicator_type.
    INVALID_TYPE_OF_PRICE(ErrorType.INVALID_REQUEST), //type_of_price is invalid	Проверьте параметры запроса. Некорректный type_of_price.
    INVALID_PARAMETER_LENGTH(ErrorType.INVALID_REQUEST), //length is invalid	Проверьте параметры запроса. Некорректный length.
    INVALID_PARAMETER_CANDLE_SOURCE_TYPE(ErrorType.INVALID_REQUEST), //input candle_source_type is invalid	Проверьте параметры запроса. Некорректный параметр candle_source_type.
    ORDER_REQUEST_ID_NOT_UUID(ErrorType.INVALID_REQUEST), //order_request_id not in UUID format	Входной параметр order_request_id имеет некорректное значение. Укажите параметр order_request_id в UUID формате. Максимальная длина — 36 символов.
    SIGNAL_ID_NOT_UUID(ErrorType.INVALID_REQUEST), //signal_id not in UUID format	Входной параметр signal_id имеет некорректное значение. Укажите параметр signal_id в UUID формате. Максимальная длина — 36 символов.
    STRATEGY_ID_UUID(ErrorType.INVALID_REQUEST), //strategy_id not in UUID format	Входной параметр strategy_id имеет некорректное значение. Укажите параметр strategy_id в UUID формате. Максимальная длина — 36 символов.
    INVALID_STRATEGY_TYPE(ErrorType.INVALID_REQUEST), //strategy_type is not valid	Входной параметр strategy_type имеет некорректное значение.
    INVALID_PARAMETER_ACTIVE(ErrorType.INVALID_REQUEST), //active is not valid	Входной параметр active имеет некорректное значение.
    INSUFFICIENT_PRIVILEGES(ErrorType.PERMISSION_DENIED), //Insufficient privileges	Недостаточно прав для совершения операции.
    INVALID_AUTHENTICATION_TOKEN(ErrorType.PERMISSION_DENIED), //Authentication token is missing or invalid	Токен доступа не найден или неактивен.
    ORDERS_ARE_NOT_AVAILABLE_ON_THIS_ACCOUNT(ErrorType.PERMISSION_DENIED), //Working with orders is not available with this account	Выставление заявок недоступно с текущего аккаунта.
    EXCHANGE_NOT_FOUND(ErrorType.NOT_FOUND), //Exchange not found	Биржа не найдена по переданному exchange_id.
    INSTRUMENT_NOT_FOUND(ErrorType.NOT_FOUND), //Instrument not found	Инструмент не найден.
    ACCOUNT_NOT_FOUND(ErrorType.NOT_FOUND), //Account not found	Счёт по переданному account_id не найден.
    ORDER_NOT_FOUND(ErrorType.NOT_FOUND), //Order not found	Торговое поручение по переданному order_id не найдено.
    POSITION_NOT_FOUND(ErrorType.NOT_FOUND), //Order not found	Торговое поручение по переданному order_id не найдено.
    STOP_ORDER_NOT_FOUND(ErrorType.NOT_FOUND), //Stop-order not found	Стоп-заявка по переданному stop_order_id не найдена.
    TASK_NOT_FOUND(ErrorType.NOT_FOUND), //Task not found	Задача не найдена.
    ASSET_NOT_FOUND(ErrorType.NOT_FOUND), //Asset not found	Актив не найден.
    BRAND_NOT_FOUND(ErrorType.NOT_FOUND), //Brand not found	Бренд не найден.
    SIGNAL_NOT_FOUND(ErrorType.NOT_FOUND), //Signal not found	Сигнал не найден. Укажите корректный идентификатор сигнала.
    INTERNAL_ERROR(ErrorType.INTERNAL_ERROR), //Internal error	Внутренняя ошибка сервиса.
    INTERNAL_NETWORK_ERROR(ErrorType.INTERNAL_ERROR), //Internal network error	Неизвестная сетевая ошибка, попробуйте выполнить запрос позже.
    INTERNAL_ERROR_TRY_LATER(ErrorType.INTERNAL_ERROR), //Internal error, please try again later	Внутренняя ошибка сервиса, попробуйте выполнить запрос позже.
    LIMIT_OF_OPEN_STREAMS_EXCEEDED(ErrorType.RESOURCE_EXHAUSTED), //Limit of open streams exceeded	Превышен лимит одновременных открытых stream-соединений. Подробнее про лимитную политику
    REQUEST_LIMIT_EXCEEDED(ErrorType.RESOURCE_EXHAUSTED), //Request limit exceeded	Превышен лимит запросов в минуту. Подробнее про лимитную политику
    NO_ACTIVE_SUBSCRIPTIONS(ErrorType.RESOURCE_EXHAUSTED), //No active subscriptions	В стриме отсутствуют активные подписки.
    NEED_CONFIRMATION(ErrorType.FAILED_PRECONDITION), //Need confirmation: %s	Требуется подтверждение операции.
    ONLY_FOR_QUALIFIED_INVESTORS(ErrorType.FAILED_PRECONDITION), //Only for qualified investors	Торговля этим инструментом доступна только квалифицированным инвесторам.
    PRICE_IS_TOO_HIGH(ErrorType.FAILED_PRECONDITION); //The price is too high	Цена заявки слишком высокая. Разбейте заявку на заявки меньшего размера. Подробнее про ограничения на стоимость заявки.

    private final int code;

    private final ErrorType errorType;

    private String messagePattern;

    ErrorCode(ErrorType errorType) {
        this(errorType, errorType.getNextCode());
    }

    ErrorCode(ErrorType errorType, int code) {
        if (code < errorType.getLastCode()) {
            throw new RuntimeException(String.format("Code %s has been already reserved", code));
        }

        this.errorType = errorType;
        this.code = code;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getCode() {
        return code;
    }

    public String getMessagePattern() {
        return messagePattern;
    }
}
