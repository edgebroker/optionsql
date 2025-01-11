package org.optionsql.broker.tws;

import com.ib.client.Contract;

import java.util.concurrent.CompletableFuture;

public class MarketPrice extends TwsListenerAdapter {
    private final TwsSession session;
    private CompletableFuture<Void> fetchFuture;
    private double currentPrice = 0.0;

    public MarketPrice(TwsSession session) {
        this.session = session;
    }

    public CompletableFuture<Void> fetchMarketPrice(String symbol) {
        fetchFuture = new CompletableFuture<>();

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");

        session.requestMarketData(contract, this);
        return fetchFuture;
    }

    @Override
    public void onTickPrice(int tickerId, int field, double price) {
        if (field == 4) { // Last Price
            currentPrice = price;
            fetchFuture.complete(null);
        }
    }

    @Override
    public void onError(int id, int errorCode, String errorMsg) {
        fetchFuture.completeExceptionally(new RuntimeException("Error " + errorCode + ": " + errorMsg));
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}
