package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.order.response.ExecutionStatus;

public class OrderExecutionReportStatusTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus toTinkoff(ExecutionStatus status) {
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

    public static ExecutionStatus toContract(ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus status) {
        return switch (status) {
            case EXECUTION_REPORT_STATUS_UNSPECIFIED -> ExecutionStatus.UNSPECIFIED;
            case EXECUTION_REPORT_STATUS_NEW -> ExecutionStatus.NEW;
            case EXECUTION_REPORT_STATUS_REJECTED -> ExecutionStatus.REJECTED;
            case EXECUTION_REPORT_STATUS_CANCELLED -> ExecutionStatus.CANCELLED;
            case EXECUTION_REPORT_STATUS_FILL -> ExecutionStatus.FILL;
            case EXECUTION_REPORT_STATUS_PARTIALLYFILL -> ExecutionStatus.PARTIALLYFILL;
            case UNRECOGNIZED -> null;
        };
    }
}
