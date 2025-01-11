package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;
import java.util.concurrent.CompletableFuture;

public class HistIV extends TwsListenerAdapter {
    private final TwsSession session;
    private CompletableFuture<Void> fetchFuture;
    private double historicalLowIV = Double.MAX_VALUE;
    private double historicalHighIV = Double.MIN_VALUE;

    public HistIV(TwsSession session) {
        this.session = session;
    }

    public CompletableFuture<Void> fetchHistoricalIV(String symbol) {
        fetchFuture = new CompletableFuture<>();
        historicalLowIV = Double.MAX_VALUE;
        historicalHighIV = Double.MIN_VALUE;

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");

        session.requestHistoricalData(contract, "1 Y", "1 day", "OPTION_IMPLIED_VOLATILITY", this);

        return fetchFuture;
    }

    @Override
    public void onHistoricalData(int reqId, Bar bar) {
        historicalLowIV = Math.min(historicalLowIV, bar.low());
        historicalHighIV = Math.max(historicalHighIV, bar.high());
    }

    @Override
    public void onHistoricalDataEnd(int reqId, String startDate, String endDate) {
        if (!fetchFuture.isDone()) {
            fetchFuture.complete(null);
        }
    }

    @Override
    public void onError(int id, int errorCode, String errorMsg) {
        fetchFuture.completeExceptionally(new RuntimeException("Error " + errorCode + ": " + errorMsg));
    }

    @Override
    public void onConnectionClosed() {
        fetchFuture.completeExceptionally(new RuntimeException("Connection closed unexpectedly"));
    }

    public double getHistoricalLowIV() {
        return historicalLowIV;
    }

    public double getHistoricalHighIV() {
        return historicalHighIV;
    }
}
