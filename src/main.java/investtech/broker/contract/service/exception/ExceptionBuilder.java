package investtech.broker.contract.service.exception;

public class ExceptionBuilder {
    protected ErrorCode errorCode;

    protected String message;

    protected String invalidAttribute;

    protected Throwable suppressedException;

    public ExceptionBuilder(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ExceptionBuilder withMessage(String message) {
        this.message = message;

        return this;
    }

    public ExceptionBuilder withInvalidAttribute(String invalidAttribute) {
        this.invalidAttribute = invalidAttribute;

        return this;
    }

    public ExceptionBuilder withSuppressedException(Throwable suppressedException) {
        this.suppressedException = suppressedException;

        return this;
    }

    public AbstractException build() {
        Class<? extends AbstractException> exceptionClass = switch (errorCode.getErrorType()) {
            case UNIMPLEMENTED -> UnimplementedException.class;
            case UNAVAILABLE -> UnavailableException.class;
            case UNKNOWN -> UnknownException.class;
            case INVALID_REQUEST -> InvalidRequestException.class;
            case PERMISSION_DENIED -> PermissionDeniedException.class;
            case NOT_FOUND -> NotFoundException.class;
            case INTERNAL_ERROR -> InternalErrorException.class;
            case RESOURCE_EXHAUSTED -> ResourceExhaustedException.class;
            case FAILED_PRECONDITION -> FailedPreconditionException.class;
        };

        return buildException(exceptionClass);
    }

    protected <T extends AbstractException> T buildException(Class<T> classException) {
        T exception;

        try {
            exception = classException
                    .getDeclaredConstructor(ErrorCode.class, String.class)
                    .newInstance(errorCode, message);
        } catch (ReflectiveOperationException newInstanceException) {
            throw new RuntimeException(newInstanceException);
        }

        if (null != suppressedException) {
            exception.addSuppressed(suppressedException);
        }

        if (exception instanceof InvalidRequestException) {
            ((InvalidRequestException) exception).setInvalidAttribute(invalidAttribute);
        }

        return exception;
    }

    public static AbstractException create(ErrorCode errorCode, String message) {
        return newBuilder(errorCode).withMessage(message).build();
    }

    public static AbstractException create(ErrorCode errorCode) {
        return newBuilder(errorCode).build();
    }

    public static ExceptionBuilder newBuilder(ErrorCode errorCode) {
        return new ExceptionBuilder(errorCode);
    }
}
