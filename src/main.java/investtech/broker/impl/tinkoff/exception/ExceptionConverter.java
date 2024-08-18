package investtech.broker.impl.tinkoff.exception;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import ru.tinkoff.piapi.core.exception.ApiRuntimeException;

import java.util.function.Supplier;

public class ExceptionConverter {
    public static <T> T rethrowContractExceptionOnError(Supplier<T> supplier) throws AbstractException {
        try {
            return supplier.get();
        } catch (ApiRuntimeException exception) {
            throw toContractException(exception);
        }
    }

    public static AbstractException toContractException(ApiRuntimeException exception) {
        int code = Integer.parseInt(exception.getCode());

        var errorCode = switch (code) {
            case 12001 -> ErrorCode.UNIMPLEMENTED; //Method is unimplemented	Метод не реализован.
            case 12002 -> ErrorCode.UNAVAILABLE; //Deprecated method is unavailable

            case 30001 -> ErrorCode.MISSING_PARAMETER_FROM; //Missing parameter
            case 30002 -> ErrorCode.PERIOD_WEEK_EXCEED; //The required period should not exceed 7 days
            case 30003 -> ErrorCode.FROM_LESS_THAN_CURRENT_DATE; //from can
            case 30004 -> ErrorCode.MISSING_PARAMETER_TO; //Missing parameter
            case 30007 -> ErrorCode.MISSING_PARAMETER_ID; //Missing parameter
            case 30009 -> ErrorCode.INVALID_PARAMETER_FROM; //from is invalid
            case 30010 -> ErrorCode.INVALID_PARAMETER_TO; //to is invalid
            case 30011 -> ErrorCode.INVALID_INTERVAL; //interval is invalid
            case 30012 -> ErrorCode.TO_LESS_THAN_FROM; //to can
            case 30014 ->
                    ErrorCode.CANDLE_PERIOD_EXCEED; //The maximum request period for the given candle interval has been exceeded
            case 30015 -> ErrorCode.MISSING_PARAMETER_QUANTITY; //Parameter quantity is missing or equal to 0
            case 30016 -> ErrorCode.INVALID_PARAMETER_QUANTITY; //quantity is invalid
            case 30017 -> ErrorCode.MISSING_PARAMETER_PRICE; //Missing parameter
            case 30018 -> ErrorCode.PRICE_MUST_BE_POSITIVE; //price is invalid
            case 30019 -> ErrorCode.MISSING_PARAMETER_DIRECTION; //Missing parameter
            case 30020 -> ErrorCode.INVALID_PARAMETER_DIRECTION; //direction is invalid
            case 30021 -> ErrorCode.MISSING_PARAMETER_ACCOUNT_ID; //Missing parameter
            case 30022 -> ErrorCode.INVALID_PARAMETER_ACCOUNT_ID; //account_id is invalid
            case 30023 -> ErrorCode.INVALID_PARAMETER_STATE; //Invalid parameter
            case 30025 -> ErrorCode.MISSING_PARAMETER_ORDER_TYPE; //Missing parameter
            case 30026 -> ErrorCode.INVALID_PARAMETER_ORDER_TYPE; //Invalid parameter
            case 30027 -> ErrorCode.MISSING_PARAMETER_ORDER_ID; //Missing parameter
            case 30028 -> ErrorCode.INVALID_PARAMETER_ORDER_ID; //order id has invalid UUID format
            case 30029 -> ErrorCode.MISSING_PARAMETER_IDEMPOTENCY_KEY; //Missing parameter
            case 30030 -> ErrorCode.INVALID_PARAMETER_IDEMPOTENCY_KEY; //idempotency_key is invalid
            case 30031 -> ErrorCode.MISSING_PARAMETER_DEPTH; //Missing parameter
            case 30032 -> ErrorCode.INVALID_PARAMETER_DEPTH; //depth is invalid
            case 30034 -> ErrorCode.INSUFFICIENT_BALANCE; //Not enough balance
            case 30036 -> ErrorCode.MISSING_PARAMETER_STOP_PRICE; //Missing parameter
            case 30037 -> ErrorCode.MISSING_PARAMETER_STOP_ORDER_TYPE; //Missing parameter
            case 30038 -> ErrorCode.INVALID_PARAMETER_STOP_ORDER_TYPE; //stop_order_type is invalid
            case 30040 -> ErrorCode.INVALID_EXPIRE_DATE; //expire_date is invalid
            case 30048 -> ErrorCode.INSTRUMENT_IS_NOT_BOND; //Instrument type is not bond
            case 30049 -> ErrorCode.POST_ORDER_ERROR; //Post order error
            case 30050 -> ErrorCode.INVALID_INSTRUMENT_STATUS; //instrument_status is invalid
            case 30052 -> ErrorCode.INSTRUMENT_UNAVAILABLE_FOR_TRADING; //Instrument forbidden for trading by API
            case 30053 -> ErrorCode.POST_STOP_ORDER_ERROR; //Post stop_order error
            case 30054 -> ErrorCode.INSTRUMENT_IS_NOT_SHARE; //Instrument type is not a share or etf
            case 30055 -> ErrorCode.TOO_LONG_ORDER_ID; //order_id cannot be longer than 36 characters	order_id
            case 30056 -> ErrorCode.STOP_ORDER_CURRENCY_UNSUPPORTED; //Stop order settlement currency is not supported
            case 30057 -> ErrorCode.DUPLICATE_ORDER; //The order is a duplicate
            case 30058 -> ErrorCode.TASK_STILL_RUNNING; //Task not completed yet
            case 30059 -> ErrorCode.CANCEL_ORDER_ERROR; //Cancel order error
            case 30060 -> ErrorCode.CANCEL_STOP_ORDER_ERROR; //Cancel stop
            case 30061 -> ErrorCode.PARAMETER_FROM_OUT_OF_RANGE; //from value out of range
            case 30062 -> ErrorCode.PARAMETER_TO_OUT_OF_RANGE; //to value out of range
            case 30063 -> ErrorCode.PARAMETER_EXPIRE_DATE_OUT_OF_RANGE; //expire_date value out of range
            case 30064 -> ErrorCode.PERIOD_MONTH_EXCEED; //The required period should not exceed 31 days
            case 30065 -> ErrorCode.MISSING_PARAMETER_TASK_ID; //Missing parameter
            case 30066 -> ErrorCode.MISSING_PARAMETER_PAYLOAD; //Missing parameter
            case 30067 -> ErrorCode.INVALID_ACTION_TYPE; //action_type is invalid
            case 30068 -> ErrorCode.ONLY_LIMIT_ORDER_ALLOWED; //Only limit order is allowed
            case 30069 -> ErrorCode.INVALID_PARAMETER_LIMIT; //Invalid parameter
            case 30070 -> ErrorCode.FROM_MORE_THAN_CURRENT_DATE; //from can
            case 30078 -> ErrorCode.INVALID_MINIMUM_PRICE_INCREMENT; //Incorrect minimum price increment
            case 30079 -> ErrorCode.INSTRUMENT_UNAVAILABLE_FOR_TRADING; //Instrument is not available for trading
            case 30080 -> ErrorCode.QUANTITY_MUST_BE_POSITIVE; //quantity must be positive
            case 30081 -> ErrorCode.ACCOUNT_CLOSED; //Account status is closed
            case 30082 -> ErrorCode.ACCOUNT_BLOCKED; //Account status is blocked
            case 30083 -> ErrorCode.INVALID_PARAMETER_ORDER_TYPE; //order_type is invalid
            case 30084 -> ErrorCode.PERIOD_MAX_LIMIT_REQUEST_EXCEED; //Maximum request period has been exceeded
            case 30086 -> ErrorCode.INVALID_YEAR; //Year is invalid
            case 30087 -> ErrorCode.MISSING_PARAMETER_QUERY; //Missing parameter
            case 30088 -> ErrorCode.FROM_AND_TO_MUST_HAVE_SAME_YEAR; //from and to must have the same year
            case 30089 -> ErrorCode.TO_MUST_NOT_BE_LATER_THAN; //to must not be later than
            case 30092 -> ErrorCode.TRADING_UNAVAILABLE_ON_WEEKENDS; //Trading unavailable on weekends
            case 30093 -> ErrorCode.MISSING_PARAMETER_INSTRUMENT_ID; //Missing parameter
            case 30095 -> ErrorCode.REQUEST_WAS_NOT_EXECUTED; //The request was not executed by the exchange
            case 30096 -> ErrorCode.ORDER_IS_REJECTED; //The order was rejected
            case 30097 -> ErrorCode.INAPPROPRIATE_TRADING_SESSION; //Inappropriate trading session
            case 30099 ->
                    ErrorCode.INSTRUMENT_PRICE_IS_OUT_OF_LIMITS; //The price is outside the limits for this instrument
            case 30100 -> ErrorCode.PRICE_MUST_BE_POSITIVE; //The price must be positive
            case 30103 -> ErrorCode.ONLY_BEST_PRICE_IS_ALLOWED; //Only best price is allowed
            case 30104 -> ErrorCode.INVALID_PRICE_TYPE; //price_type is invalid
            case 30106 -> ErrorCode.INVALID_STATUS; //status is invalid
            case 30107 -> ErrorCode.INVALID_PAGINATION_PARAMETERS; //Negative values are not allowed
            case 30109 ->
                    ErrorCode.MAXIMUM_TRANSACTION_AMOUNT_HAS_BEEN_EXCEEDED; //Maximum transaction amount has been exceeded
            case 30212 -> ErrorCode.MISSING_PARAMETER_SPREAD_TYPE; //spread type required
            case 30213 -> ErrorCode.INVALID_INDICATOR_TYPE; //indicator_type is invalid
            case 30214 -> ErrorCode.INVALID_TYPE_OF_PRICE; //type_of_price is invalid
            case 30215 -> ErrorCode.INVALID_PARAMETER_LENGTH; //length is invalid
            case 30219 -> ErrorCode.INVALID_PARAMETER_CANDLE_SOURCE_TYPE; //input candle_source_type is invalid
            case 30221 -> ErrorCode.ORDER_REQUEST_ID_NOT_UUID; //order_request_id not in UUID format
            case 30222 -> ErrorCode.SIGNAL_ID_NOT_UUID; //signal_id not in UUID format
            case 30223 -> ErrorCode.STRATEGY_ID_UUID; //strategy_id not in UUID format
            case 30224 -> ErrorCode.INVALID_STRATEGY_TYPE; //strategy_type is not valid
            case 30225 -> ErrorCode.INVALID_PARAMETER_ACTIVE; //active is not valid
            case 30226 ->
                    ErrorCode.INVALID_PAGINATION_PARAMETERS; //Specify a lower limit or page value because there are fewer items

            case 40002 -> ErrorCode.INSUFFICIENT_PRIVILEGES; //Insufficient privileges
            case 40003 -> ErrorCode.INVALID_AUTHENTICATION_TOKEN; //Authentication token is missing or invalid
            case 40004 ->
                    ErrorCode.ORDERS_ARE_NOT_AVAILABLE_ON_THIS_ACCOUNT; //Working with orders is not available with this account

            case 50001 -> ErrorCode.EXCHANGE_NOT_FOUND; //Exchange not found
            case 50002 -> ErrorCode.INSTRUMENT_NOT_FOUND; //Instrument not found
            case 50004 -> ErrorCode.ACCOUNT_NOT_FOUND; //Account not found
            case 50005 -> ErrorCode.ORDER_NOT_FOUND; //Order not found
            case 50006 -> ErrorCode.STOP_ORDER_NOT_FOUND; //Stop
            case 50007 -> ErrorCode.TASK_NOT_FOUND; //Task not found
            case 50009 -> ErrorCode.ASSET_NOT_FOUND; //Asset not found
            case 50010 -> ErrorCode.BRAND_NOT_FOUND; //Brand not found
            case 50012 -> ErrorCode.SIGNAL_NOT_FOUND; //Signal not found

            case 70001 -> ErrorCode.INTERNAL_ERROR; //Internal error
            case 70002 -> ErrorCode.INTERNAL_NETWORK_ERROR; //Internal network error
            case 70003 -> ErrorCode.INTERNAL_ERROR_TRY_LATER; //Internal error

            case 80001 -> ErrorCode.LIMIT_OF_OPEN_STREAMS_EXCEEDED; //Limit of open streams exceeded
            case 80002 -> ErrorCode.REQUEST_LIMIT_EXCEEDED; //Request limit exceeded
            case 80004 -> ErrorCode.NO_ACTIVE_SUBSCRIPTIONS; //No active subscriptions

            case 90001 -> ErrorCode.NEED_CONFIRMATION; //Need confirmation
            case 90002 -> ErrorCode.ONLY_FOR_QUALIFIED_INVESTORS; //Only for qualified investors
            case 90003 -> ErrorCode.PRICE_IS_TOO_HIGH; //The price is too high
            default -> ErrorCode.UNKNOWN;
        };

        return ExceptionBuilder
                .newBuilder(errorCode)
                .withMessage(exception.getMessage())
                .withSuppressedException(exception)
                .build();
    }
}
