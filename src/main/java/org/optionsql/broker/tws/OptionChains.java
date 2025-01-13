package org.optionsql.broker.tws;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Types.Right;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class OptionChains {

    private final TwsRequestManager requestManager;
    private final Map<String, List<JsonObject>> optionChainData = new HashMap<>();

    public OptionChains(TwsRequestManager requestManager) {
        this.requestManager = requestManager;
    }

    /**
     * Fetch the entire option chain for a symbol.
     */
    public CompletableFuture<Void> fetchOptionChains(String symbol) {
        CompletableFuture<Void> fetchFuture = new CompletableFuture<>();

        requestManager.enqueueRequest(new TwsRequest() {
            @Override
            public void execute(TwsSession sess, Runnable onComplete) {
                // First: Fetch the conid for the symbol
                sess.requestContractDetails(symbol, new TwsListenerAdapter() {
                    @Override
                    public void onContractDetails(int reqId, ContractDetails contractDetails) {
                        int conid = contractDetails.conid();

                        // Second: Use conid to fetch option expirations and strikes together
                        sess.requestOptionExpirations(symbol, conid, new TwsListenerAdapter() {
                            @Override
                            public void onOptionExpirations(List<String> expirations, List<Double> strikes) {
                                List<String> filteredExpirations = expirations.stream().filter(weeklyMonthlyFilter).toList();
                                List<CompletableFuture<Void>> optionFutures = new ArrayList<>();

                                for (String expiration : filteredExpirations) {
                                    for (Double strike : strikes) {
                                        CompletableFuture<Void> optionFuture = new CompletableFuture<>();
                                        optionFutures.add(optionFuture);

                                        fetchOptionData(sess, symbol, expiration, strike, optionFuture, onComplete);
                                    }
                                }

                                CompletableFuture.allOf(optionFutures.toArray(new CompletableFuture[0]))
                                        .thenRun(() -> {
                                            fetchFuture.complete(null);
                                            onComplete.run();
                                        })
                                        .exceptionally(ex -> {
                                            fetchFuture.completeExceptionally(ex);
                                            onComplete.run();
                                            return null;
                                        });
                            }

                            @Override
                            public void onError(int id, int errorCode, String errorMsg) {
                                fetchFuture.completeExceptionally(new RuntimeException("Error " + errorCode + ": " + errorMsg));
                                onComplete.run();
                            }
                        });
                    }

                    @Override
                    public void onError(int id, int errorCode, String errorMsg) {
                        fetchFuture.completeExceptionally(new RuntimeException("Error " + errorCode + ": " + errorMsg));
                        onComplete.run();
                    }
                });
            }

            @Override
            public String getDescription() {
                return "Fetch Option Chains for: " + symbol;
            }
        });

        return fetchFuture;
    }

    /**
     * Fetches both Call and Put option data.
     */
    private void fetchOptionData(TwsSession sess, String symbol, String expiration, Double strike, CompletableFuture<Void> future, Runnable onComplete) {
        Contract callContract = createOptionContract(symbol, expiration, strike, Right.Call);
        Contract putContract = createOptionContract(symbol, expiration, strike, Right.Put);

        List<CompletableFuture<Void>> futures = Arrays.asList(
                fetchSingleOptionSide(sess, callContract, symbol, expiration, strike, "call"),
                fetchSingleOptionSide(sess, putContract, symbol, expiration, strike, "put")
        );

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> future.complete(null))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });
    }

    /**
     * Fetch option data for a specific side (call/put).
     */
    private CompletableFuture<Void> fetchSingleOptionSide(TwsSession sess, Contract contract, String symbol, String expiration, Double strike, String type) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        sess.requestOptionMarketData(contract, new TwsListenerAdapter() {
            private final JsonObject optionData = new JsonObject();

            @Override
            public void onTickPrice(int tickerId, int field, double price) {
                switch (field) {
                    case 1 -> optionData.put(type + "_bid", price);
                    case 2 -> optionData.put(type + "_ask", price);
                    case 4 -> optionData.put(type + "_mid", (optionData.getDouble(type + "_bid", 0.0) + price) / 2);
                }
            }

            @Override
            public void onTickSize(int tickerId, int field, int size) {
                if (field == 8) optionData.put(type + "_volume", size);
                if (field == 3) optionData.put(type + "_oi", size);
            }

            @Override
            public void onTickOptionComputation(int tickerId, double impliedVolatility, double delta, double gamma, double vega, double theta, double optPrice, double pvDividend) {
                optionData.put("ticker_symbol", symbol);
                optionData.put("expiration_date", expiration);
                optionData.put("strike_price", strike);
                optionData.put(type + "_delta", delta);
                optionData.put(type + "_gamma", gamma);
                optionData.put(type + "_theta", theta);
                optionData.put(type + "_vega", vega);
                optionData.put(type + "_iv", impliedVolatility);

                optionChainData.computeIfAbsent(symbol, k -> new ArrayList<>()).add(optionData);
                future.complete(null);
            }

            @Override
            public void onError(int id, int errorCode, String errorMsg) {
                future.completeExceptionally(new RuntimeException("Error " + errorCode + ": " + errorMsg));
            }
        });

        return future;
    }

    Predicate<String> weeklyMonthlyFilter = expiration -> {
        LocalDate expDate = LocalDate.parse(expiration, DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate sixMonthsLater = LocalDate.now().plusMonths(6);
        return expDate.getDayOfWeek() == DayOfWeek.FRIDAY && expDate.isBefore(sixMonthsLater);
    };

    private Contract createOptionContract(String symbol, String expiration, Double strike, Right right) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("OPT");
        contract.exchange("SMART");
        contract.currency("USD");
        contract.lastTradeDateOrContractMonth(expiration);
        contract.strike(strike);
        contract.right(right);
        return contract;
    }

    public JsonArray getOptionChainsAsJson(String symbol) {
        List<JsonObject> data = optionChainData.getOrDefault(symbol, Collections.emptyList());
        return new JsonArray(data);
    }
}
