package org.optionsql.broker.tws.request;

import com.ib.client.*;
import io.vertx.core.Vertx;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class TwsSession extends DefaultEWrapper {

    private final Vertx vertx;
    private final EJavaSignal signal;
    private final EClientSocket clientSocket;
    private final AtomicInteger requestIdCounter = new AtomicInteger();
    private final Logger logger;
    private final Map<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private CompletableFuture<Void> connectFuture;

    public TwsSession(Vertx vertx, Logger logger) {
        this.vertx = vertx;
        this.logger = logger;
        signal = new EJavaSignal();
        clientSocket = new EClientSocket(this, signal);
    }

    public Logger getLogger() {
        return logger;
    }

    public CompletableFuture<Void> connect(String host, int port, int clientId) {
        connectFuture = new CompletableFuture<>();
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

    @Override
    public void connectAck() {
        vertx.runOnContext(v -> {
            if (clientSocket.isConnected()) {
                logger.info("Successfully connected to TWS.");
                connectFuture.complete(null);
            } else {
                logger.severe("Failed to connect to TWS.");
                connectFuture.completeExceptionally(new RuntimeException("Connection failed."));
            }
        });
    }

    public void disconnect() {
        clientSocket.eDisconnect();
        logger.info("Disconnected from TWS.");
    }

    public void requestMarketData(Contract contract, TwsListener listener) {
        int reqId = requestIdCounter.incrementAndGet();
        PendingRequest request = new PendingRequest(listener, new CompletableFuture<>());
        pendingRequests.put(reqId, request);

        // Request live market data
        clientSocket.reqMktData(reqId, contract, "", true, false, null);
    }

    public void requestHistoricalData(Contract contract, String duration, String barSize, String whatToShow, TwsListener listener) {
        int reqId = requestIdCounter.incrementAndGet();
        PendingRequest request = new PendingRequest(listener, new CompletableFuture<>());
        pendingRequests.put(reqId, request);

        clientSocket.reqHistoricalData(reqId, contract, "", duration, barSize, whatToShow, 1, 1, false, null);
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        vertx.runOnContext(v -> {
            PendingRequest request = pendingRequests.get(reqId);
            if (request != null) {
                request.listener.onHistoricalData(reqId, bar);
            }
        });
    }

    @Override
    public void historicalDataEnd(int reqId, String startDate, String endDate) {
        vertx.runOnContext(v -> {
            PendingRequest request = pendingRequests.get(reqId);
            if (request != null) {
                request.listener.onHistoricalDataEnd(reqId, startDate, endDate);
                pendingRequests.remove(reqId);  // Clean up the request
            }
        });
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        vertx.runOnContext(v -> {
            PendingRequest request = pendingRequests.get(reqId);

            if (request != null) {
                request.listener.onContractDetails(reqId, contractDetails);
                pendingRequests.remove(reqId);
            }
        });
    }

    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId, String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {
        if (exchange.equals("SMART")) {
            vertx.runOnContext(v -> {
                PendingRequest request = pendingRequests.get(reqId);
                if (request != null) {
                    request.listener.onOptionExpirations(new ArrayList<>(expirations), new ArrayList<>(strikes));
                    pendingRequests.remove(reqId);
                }
            });
        }
    }

    @Override
    public void tickOptionComputation(int reqId, int field, int tickAttrib, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        vertx.runOnContext(v -> {
            PendingRequest request = pendingRequests.get(reqId);
            if (request != null) {
                if (request.listener.onTickOptionComputation(reqId, impliedVol, delta, gamma, vega, theta, optPrice, pvDividend)) {
                    pendingRequests.remove(reqId);
                }
            }
        });
    }

    @Override
    public void tickPrice(int reqId, int field, double price, TickAttrib attribs) {
        vertx.runOnContext(v -> {
            PendingRequest request = pendingRequests.get(reqId);
            if (request != null) {
                if (request.listener.onTickPrice(reqId, field, price)){
                    pendingRequests.remove(reqId);
                }
            }
        });
    }

    @Override
    public void error(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        vertx.runOnContext(v -> {
            if (errorCode == 2104 || errorCode == 2106 || errorCode == 2158) {
                logger.info("[TWS Status] Code: " + errorCode + ", Msg: " + errorMsg);
                return;  // Non-critical status, no further action
            }

            PendingRequest request = pendingRequests.get(id);
            if (request != null) {
                request.listener.onError(id, errorCode, errorMsg);
                request.future.completeExceptionally(new RuntimeException(errorMsg));
                pendingRequests.remove(id);
            } else {
                logger.severe("Error [ID: " + id + "] Code: " + errorCode + ", Msg: " + errorMsg);
            }
        });
    }

    // ===========================
    // CONTRACT HELPERS
    // ===========================

    private Contract createStockContract(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");
        return contract;
    }

    private Contract createOptionContract(String symbol, String expiration, double strike, String right) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("OPT");
        contract.exchange("SMART");
        contract.currency("USD");
        contract.lastTradeDateOrContractMonth(expiration);
        contract.strike(strike);
        contract.right(right);  // "C" for Call, "P" for Put
        contract.multiplier("100");
        return contract;
    }

    // ===========================
    // PENDING REQUEST HANDLER
    // ===========================

    private static class PendingRequest {
        private TwsListener listener;
        private final CompletableFuture<Double> future;

        public PendingRequest(TwsListener listener, CompletableFuture<Double> future) {
            this.listener = listener;
            this.future = future;
        }
    }
}
