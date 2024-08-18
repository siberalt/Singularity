package investtech.broker.contract.service.exception;

public class UnimplementedException extends AbstractException {
    public UnimplementedException(String message) {
        super(ErrorCode.UNIMPLEMENTED, message);
    }

    public UnimplementedException() {
        super(ErrorCode.UNIMPLEMENTED);
    }
}
