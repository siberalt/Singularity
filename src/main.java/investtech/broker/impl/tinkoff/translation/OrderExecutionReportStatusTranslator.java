package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.response.OrderExecutionReportStatus;

public class OrderExecutionReportStatusTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus toTinkoff(OrderExecutionReportStatus status) {
        return switch (status) {
            case UNSPECIFIED ->
                    ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_UNSPECIFIED;
            case NEW -> ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW;
            case REJECTED -> ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED;
            case CANCELLED -> ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED;
            case FILL -> ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL;
            case PARTIALLYFILL ->
                    ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL;
        };
    }

    public static OrderExecutionReportStatus toContract(ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus status) {
        return switch (status) {
            case EXECUTION_REPORT_STATUS_UNSPECIFIED -> OrderExecutionReportStatus.UNSPECIFIED;
            case EXECUTION_REPORT_STATUS_NEW -> OrderExecutionReportStatus.NEW;
            case EXECUTION_REPORT_STATUS_REJECTED -> OrderExecutionReportStatus.REJECTED;
            case EXECUTION_REPORT_STATUS_CANCELLED -> OrderExecutionReportStatus.CANCELLED;
            case EXECUTION_REPORT_STATUS_FILL -> OrderExecutionReportStatus.FILL;
            case EXECUTION_REPORT_STATUS_PARTIALLYFILL -> OrderExecutionReportStatus.PARTIALLYFILL;
            case UNRECOGNIZED -> null;
        };
    }
}
