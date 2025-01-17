package org.optionsql.broker.tws.task;

import com.ib.client.Contract;
import org.optionsql.broker.tws.request.TwsListenerAdapter;
import org.optionsql.broker.tws.request.TwsRequest;
import org.optionsql.broker.tws.request.TwsRequestManager;
import org.optionsql.broker.tws.request.TwsSession;

import java.util.concurrent.CompletableFuture;

public class MarketPrice {

    private final TwsRequestManager requestManager;
    private CompletableFuture<Void> fetchFuture;
    private double currentPrice = -1;

    public MarketPrice(TwsRequestManager requestManager) {
        this.requestManager = requestManager;
    }

    public CompletableFuture<Void> fetchMarketPrice(String symbol) {
        fetchFuture = new CompletableFuture<>();

        Contract contract = createStockContract(symbol);

        // Enqueue market data request
        requestManager.enqueueRequest(new TwsRequest() {
            @Override
            public void execute(TwsSession sess, Runnable onComplete) {
                sess.requestMarketData(contract, new TwsListenerAdapter() {
                    @Override
                    public boolean onTickPrice(int tickerId, int field, double price) {
                        if (currentPrice == -1 && (field == 4 || field == 9)) { // LAST_PRICE or CLOSE_PRICE
                            currentPrice = price;
                            completeRequest(null, onComplete);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onError(int id, int errorCode, String errorMsg) {
                        completeRequest(new RuntimeException("Error " + errorCode + ": " + errorMsg), onComplete);
                    }

                    @Override
                    public void onConnectionClosed() {
                        completeRequest(new RuntimeException("Connection closed unexpectedly"), onComplete);
                    }

                    private void completeRequest(Throwable error, Runnable onComplete) {
                        if (!fetchFuture.isDone()) {
                            if (error == null) {
                                fetchFuture.complete(null);
                            } else {
                                fetchFuture.completeExceptionally(error);
                            }
                            onComplete.run();  // Notify the request manager
                        }
                    }
                });
            }

            @Override
            public String getDescription() {
                return "Fetch Market Price for: " + symbol;
            }
        });

        return fetchFuture;
    }

    private Contract createStockContract(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");
        return contract;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}
