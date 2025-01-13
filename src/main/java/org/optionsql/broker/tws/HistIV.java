package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;
import java.util.concurrent.CompletableFuture;

public class HistIV {

    private final TwsRequestManager requestManager;
    private CompletableFuture<Void> fetchFuture;
    private double historicalLowIV = Double.MAX_VALUE;
    private double historicalHighIV = Double.MIN_VALUE;

    public HistIV(TwsRequestManager requestManager) {
        this.requestManager = requestManager;
    }

    public CompletableFuture<Void> fetchHistoricalIV(String symbol) {
        fetchFuture = new CompletableFuture<>();
        historicalLowIV = Double.MAX_VALUE;
        historicalHighIV = Double.MIN_VALUE;

        Contract contract = createStockContract(symbol);

        // Create and enqueue the request
        requestManager.enqueueRequest(new TwsRequest() {
            @Override
            public void execute(TwsSession sess, Runnable onComplete) {
                sess.requestHistoricalData(contract, "1 Y", "1 month", "OPTION_IMPLIED_VOLATILITY", new TwsListenerAdapter() {
                    @Override
                    public void onHistoricalData(int reqId, Bar bar) {
                        historicalLowIV = Math.min(historicalLowIV, bar.low());
                        historicalHighIV = Math.max(historicalHighIV, bar.high());
                    }

                    @Override
                    public void onHistoricalDataEnd(int reqId, String startDate, String endDate) {
                        completeRequest(null, onComplete);
                    }

                    @Override
                    public void onError(int id, int errorCode, String errorMsg) {
                        completeRequest(new RuntimeException("Error " + errorCode + ": " + errorMsg), onComplete);
                    }

                    @Override
                    public void onConnectionClosed() {
                        completeRequest(new RuntimeException("Connection closed unexpectedly"), onComplete);
                    }
                });
            }

            @Override
            public String getDescription() {
                return "Fetch Historical IV for: " + symbol;
            }

            private void completeRequest(Throwable error, Runnable onComplete) {
                if (!fetchFuture.isDone()) {
                    if (error == null) {
                        fetchFuture.complete(null);
                    } else {
                        fetchFuture.completeExceptionally(error);
                    }
                    onComplete.run();  // Notify the queue
                }
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

    public double getHistoricalLowIV() {
        return historicalLowIV;
    }

    public double getHistoricalHighIV() {
        return historicalHighIV;
    }
}
