package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingOrderService implements OrderService {

    private static final Logger DEFAULT_LOGGER = createDefaultLogger();

    private static Logger createDefaultLogger() {
        Logger logger = Logger.getLogger(LoggingOrderService.class.getName());
        // Remove all existing handlers and add System.out handler
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
        consoleHandler.setFormatter(new java.util.logging.SimpleFormatter());
        logger.addHandler(consoleHandler);
        return logger;
    }

    private final OrderService delegate;
    private Logger logger;

    public LoggingOrderService(OrderService delegate) {
        this(delegate, DEFAULT_LOGGER);
    }

    public LoggingOrderService(OrderService delegate, Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    private String methodName() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    @Override
    public GetPriceResponse getPrice(GetPriceRequest request) throws AbstractException {
        String method = methodName();
        logger.fine(() -> method + " request: " + request);
        try {
            GetPriceResponse response = delegate.getPrice(request);
            logger.fine(() -> method + " response: " + response);
            return response;
        } catch (AbstractException e) {
            logger.log(Level.WARNING, method + " exception", e);
            throw e;
        }
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        String method = methodName();
        logger.fine(() -> method + " request: " + request);
        try {
            PostOrderResponse response = delegate.post(request);
            logger.fine(() -> method + " response: " + response);
            return response;
        } catch (AbstractException e) {
            logger.log(Level.WARNING, method + " exception", e);
            throw e;
        }
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        String method = methodName();
        logger.fine(() -> method + " request: " + request);
        try {
            CancelOrderResponse response = delegate.cancel(request);
            logger.fine(() -> method + " response: " + response);
            return response;
        } catch (AbstractException e) {
            logger.log(Level.WARNING, method + " exception", e);
            throw e;
        }
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        String method = methodName();
        logger.fine(() -> method + " request: " + request);
        try {
            OrderState response = delegate.getState(request);
            logger.fine(() -> method + " response: " + response);
            return response;
        } catch (AbstractException e) {
            logger.log(Level.WARNING, method + " exception", e);
            throw e;
        }
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        String method = methodName();
        logger.fine(() -> method + " request: " + request);
        try {
            GetOrdersResponse response = delegate.get(request);
            logger.fine(() -> method + " response: " + response);
            return response;
        } catch (AbstractException e) {
            logger.log(Level.WARNING, method + " exception", e);
            throw e;
        }
    }
}
