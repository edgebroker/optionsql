package org.optionsql.broker.tws;

import com.ib.client.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class TwsSession extends DefaultEWrapper {

    private final EJavaSignal signal;
    private final EClientSocket clientSocket;
    private final AtomicInteger requestIdCounter = new AtomicInteger();
    private final Logger logger;
    private final Map<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    public TwsSession(Logger logger) {
        this.logger = logger;
        signal = new EJavaSignal();
        clientSocket = new EClientSocket(this, signal);
    }

    public CompletableFuture<Void> connect(String host, int port, int clientId) {
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        clientSocket.setAsyncEConnect(false);
        clientSocket.eConnect(host, port, clientId);

        EReader reader = new EReader(clientSocket, signal);
        reader.start();

        new Thread(() -> {
            while (clientSocket.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    logger.severe("Error processing TWS messages: " + e.getMessage());
                }
            }
        }).start();

        return connectFuture;
    }

    public void disconnect() {
        clientSocket.eDisconnect();
        logger.info("Disconnected from TWS.");
    }

    public void requestMarketData(Contract contract, TwsListener listener) {
        int reqId = requestIdCounter.incrementAndGet();
        PendingRequest request = new PendingRequest(listener, new CompletableFuture<>());
        pendingRequests.put(reqId, request);
        clientSocket.reqMktData(reqId, contract, "", false, false, null);
        logger.info("Market data request sent for: " + contract.symbol() + " with reqId: " + reqId);
    }

    public void requestHistoricalData(Contract contract, String duration, String barSize, String whatToShow, TwsListener listener) {
        int reqId = requestIdCounter.incrementAndGet();
        PendingRequest request = new PendingRequest(listener, new CompletableFuture<>());
        pendingRequests.put(reqId, request);
        clientSocket.reqHistoricalData(reqId, contract, "", duration, barSize, whatToShow, 1, 1, false, null);
        logger.info("Historical data request sent for: " + contract.symbol() + " with reqId: " + reqId);
    }

    @Override
    public void tickPrice(int reqId, int field, double price, TickAttrib attribs) {
        logger.info("Tick price for: " + reqId + " field: " + field + " price: " + price);
        PendingRequest request = pendingRequests.get(reqId);
        if (request != null) {
            request.listener.onTickPrice(reqId, field, price);
            request.future.complete(price);
        }
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        logger.info("Historical data for: " + reqId + " bar: " + bar);
        PendingRequest request = pendingRequests.get(reqId);
        if (request != null) {
            request.listener.onHistoricalData(reqId, bar);
        }
    }

    @Override
    public void historicalDataEnd(int reqId, String startDate, String endDate) {
        logger.info("Historical data End for: " + reqId + " startDate: " + startDate+ " endDate: " + endDate);
        PendingRequest request = pendingRequests.get(reqId);
        if (request != null) {
            request.listener.onHistoricalDataEnd(reqId, startDate, endDate);
            pendingRequests.remove(reqId);
        }
    }

    @Override
    public void error(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        PendingRequest request = pendingRequests.get(id);
        if (request != null) {
            request.listener.onError(id, errorCode, errorMsg);
            request.future.completeExceptionally(new RuntimeException(errorMsg));
        } else if (errorCode == 2104 || errorCode == 2106 || errorCode == 2158) {
            logger.info("[TWS Status] Code: " + errorCode + ", Msg: " + errorMsg);
        } else {
            logger.severe("Error [ID: " + id + "] Code: " + errorCode + ", Msg: " + errorMsg);
        }
    }

    // New inner class to manage pending requests
    private static class PendingRequest {
        private final TwsListener listener;
        private final CompletableFuture<Double> future;

        public PendingRequest(TwsListener listener, CompletableFuture<Double> future) {
            this.listener = listener;
            this.future = future;
        }
    }
}
