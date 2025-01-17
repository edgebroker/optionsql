package org.optionsql.fetch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MarketDataFetchService extends BaseService {

    private String marketDataApiUrl;
     private String marketDataEarningsUrl;
    private String marketDataToken;
    private JsonObject tickerConfig;
    private int fromDays;
    private int toDays;

    public MarketDataFetchService(String serviceName) {
        super(serviceName);
    }

    @Override
    public void start() throws Exception {
        try {
            super.start();

            // Load service-specific configuration
            JsonObject serviceConfig = getServiceConfig();
            JsonObject marketDataConfig = serviceConfig.getJsonObject("marketdata");

            marketDataApiUrl = marketDataConfig.getJsonObject("urls").getString("api");
            marketDataEarningsUrl = marketDataConfig.getJsonObject("urls").getString("earnings");
            marketDataToken = marketDataConfig.getString("token");
            fromDays = serviceConfig.getInteger("fromdays");
            toDays = serviceConfig.getInteger("todays");

            // Load ticker configuration from the specified file
            String tickerFilePath = serviceConfig.getString("ticker");
            tickerConfig = loadTickerConfig(tickerFilePath);

            getLogger().info("MarketDataFetchService started and ready.");
            processTickers();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private JsonObject loadTickerConfig(String filePath) {
        try {
            Path path = Path.of(filePath);
            String content = Files.readString(path);
            getLogger().info("Ticker configuration loaded from: " + filePath);
            return new JsonObject(content);
        } catch (Exception e) {
            getLogger().severe("Failed to load ticker configuration: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void processTickers() {
        getLogger().info("Processing tickers from configuration...");
        JsonArray allOptionChains = new JsonArray();

        // Create a queue of tickers to process
        Queue<JsonObject> tickerQueue = new LinkedList<>();
        tickerConfig.getJsonObject("segments").forEach(entry -> {
            String segment = entry.getKey();
            JsonArray tickers = (JsonArray) entry.getValue(); // Cast to List<String>
            tickers.forEach(ticker -> {
                tickerQueue.add(new JsonObject().put("ticker", ticker).put("segment", segment));
            });
        });

        // A promise to complete when processing finishes
        Promise<Void> processComplete = Promise.promise();

        // Start processing the queue
        processNextTicker(tickerQueue, allOptionChains, processComplete);

        // Return the future to indicate completion
        processComplete.future()
                .onSuccess(ignored -> {
                    try {

                        // Publish to fetch.complete
                        JsonObject message = new JsonObject()
                                .put("service", "fetch")
                                .put("status", "success")
                                .put("payload", allOptionChains);

                        getEventBus().publish("fetch.complete", message);

                        // Log the successful save
                        getLogger().info("Sent new dataset to fetch.complete");
                    } catch (Exception e) {
                        getLogger().severe("Exception: " + e.getMessage());
                    }
                })
                .onFailure(err -> getLogger().severe("Failed to process all tickers: " + err.getMessage()));
    }

    private void processNextTicker(Queue<JsonObject> tickerQueue, JsonArray allOptionChains, Promise<Void> processComplete) {
        if (tickerQueue.isEmpty()) {
            processComplete.complete();
            return;
        }

        JsonObject tickerInfo = tickerQueue.poll();  // Removes the ticker from the queue
        String ticker = tickerInfo.getString("ticker");
        String segment = tickerInfo.getString("segment");

        getLogger().info("Processing ticker: " + ticker);

        processSingleTicker(ticker, segment)
                .onSuccess(tickerData -> {
                    if (tickerData != null) {
                        allOptionChains.add(tickerData);
                    }
                    getLogger().info("Successfully processed ticker: " + ticker);
                    processNextTicker(tickerQueue, allOptionChains, processComplete);
                })
                .onFailure(err -> {
                    getLogger().warning("Failed to process ticker: " + ticker + " | Reason: " + err.getMessage() + ". Retrying...");
                    tickerQueue.add(tickerInfo);  // Re-add the ticker for retry
                    processNextTicker(tickerQueue, allOptionChains, processComplete);
                });
    }

    private Future<JsonObject> processSingleTicker(String ticker, String segment) {
        Promise<JsonObject> resultPromise = Promise.promise();
        long startTime = System.currentTimeMillis();

        fetchNextEarnings(ticker).compose(nextEarnings -> {
                    boolean hasEarnings = "ok".equals(nextEarnings.getString("s"));

                    Future<Double> currentPriceFuture = requestCurrentPrice(ticker);
                    Future<JsonObject> histIVFuture = requestHistoricalIV(ticker);
                    Future<JsonObject> optionChainFuture = fetchOptionChain(ticker);

                    return currentPriceFuture.compose(currentPrice ->
                            histIVFuture.compose(histIV ->
                                    optionChainFuture.map(optionChainData -> {
                                        JsonArray mergedData = mergeOptionData(optionChainData);
                                        JsonObject optionsByExpiration = transformOptionChain(mergedData);

                                        long endTime = System.currentTimeMillis();
                                        double durationSeconds = (endTime - startTime) / 1000.0;

                                        getLogger().info("Ticker: " + ticker + " | Duration: " + durationSeconds + "s | Option Chains: " + mergedData.size());

                                        JsonObject result = new JsonObject()
                                                .put("ticker_symbol", ticker)
                                                .put("current_price", currentPrice)
                                                .put("segment", segment)
                                                .put("iv_historical_low", histIV.getDouble("iv_low", 0.0))
                                                .put("iv_historical_high", histIV.getDouble("iv_high", 100.0))
                                                .put("expirations", optionsByExpiration);

                                        if (hasEarnings) {
                                            result.put("next_earnings_date", convertUnixToDate(nextEarnings.getJsonArray("reportDate").getLong(0)))
                                                    .put("next_earnings_time", nextEarnings.getJsonArray("reportTime").getString(0));
                                        } else {
                                            result.put("next_earnings_date", "N/A")
                                                    .put("next_earnings_time", "N/A");
                                        }
                                        return result;
                                    })
                            )
                    );
                }).onSuccess(resultPromise::complete)
                .onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    private Future<Double> requestCurrentPrice(String ticker) {
        Promise<Double> promise = Promise.promise();
        JsonObject request = new JsonObject().put("symbol", ticker);

        getEventBus().request("broker.request.get_current_price", request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                promise.complete(response.getDouble("current_price"));
            } else {
                promise.fail("Failed to fetch current price for ticker: " + ticker);
            }
        });

        return promise.future();
    }

    private Future<JsonObject> requestHistoricalIV(String ticker) {
        Promise<JsonObject> promise = Promise.promise();
        JsonObject request = new JsonObject().put("symbol", ticker);

        getEventBus().request("broker.request.get_hist_iv", request, reply -> {
            if (reply.succeeded()) {
                promise.complete((JsonObject) reply.result().body());
            } else {
                promise.fail("Failed to fetch historical IV for ticker: " + ticker);
            }
        });

        return promise.future();
    }

    private JsonObject transformOptionChain(JsonArray mergedData) {
        JsonObject optionsByExpiration = new JsonObject();

        for (int i = 0; i < mergedData.size(); i++) {
            JsonObject option = mergedData.getJsonObject(i);
            String expiration = option.getString("expiration");

            // Add expiration key if not already present
            if (!optionsByExpiration.containsKey(expiration)) {
                optionsByExpiration.put(expiration, new JsonArray());
            }

            // Add strike data to the corresponding expiration
            JsonObject strikeData = new JsonObject()
                    .put("strike_price", option.getDouble("strike"))
                    .put("call_bid", option.getDouble("call_bid", 0.0))
                    .put("call_ask", option.getDouble("call_ask", 0.0))
                    .put("call_mid", option.getDouble("call_mid", 0.0))
                    .put("call_volume", option.getInteger("call_volume", 0))
                    .put("call_oi", option.getInteger("call_oi", 0))
                    .put("call_delta", option.getDouble("call_delta", 0.0))
                    .put("call_gamma", option.getDouble("call_gamma", 0.0))
                    .put("call_theta", option.getDouble("call_theta", 0.0))
                    .put("call_vega", option.getDouble("call_vega", 0.0))
                    .put("call_iv", option.getDouble("call_iv", 0.0))
                    .put("put_bid", option.getDouble("put_bid", 0.0))
                    .put("put_ask", option.getDouble("put_ask", 0.0))
                    .put("put_mid", option.getDouble("put_mid", 0.0))
                    .put("put_volume", option.getInteger("put_volume", 0))
                    .put("put_oi", option.getInteger("put_oi", 0))
                    .put("put_delta", option.getDouble("put_delta", 0.0))
                    .put("put_gamma", option.getDouble("put_gamma", 0.0))
                    .put("put_theta", option.getDouble("put_theta", 0.0))
                    .put("put_vega", option.getDouble("put_vega", 0.0))
                    .put("put_iv", option.getDouble("put_iv", 0.0));

            optionsByExpiration.getJsonArray(expiration).add(strikeData);
        }

        return optionsByExpiration;
    }

    private Future<JsonObject> fetchNextEarnings(String ticker) {
        String url = marketDataEarningsUrl + ticker + "?token=" + marketDataToken;
        return makeHttpRequest(url)
                .compose(response -> {
                    if ("ok".equals(response.getString("s"))) {
                        return Future.succeededFuture(response);
                    } else {
                        JsonObject defaultResponse = new JsonObject()
                                .put("s", "no earnings");
                        return Future.succeededFuture(defaultResponse);
                    }
                });
    }

    private Future<JsonObject> fetchOptionChain(String ticker) {
        String url = marketDataApiUrl + ticker + "?token=" + marketDataToken + "&from=" +
                     getFutureDateISO8601(fromDays) + "&to=" + getFutureDateISO8601(toDays);
        return makeHttpRequest(url)
                .compose(response -> {
                    if ("ok".equals(response.getString("s"))) {
                        return Future.succeededFuture(response);
                    } else {
                        return Future.failedFuture("Failed to fetch option chain for ticker: " + ticker+", response: "+response.encodePrettily());
                    }
                });
    }

    private JsonArray mergeOptionData(JsonObject jsonResponse) {
        JsonArray mergedArray = new JsonArray();
        try {
            // Replace nulls with 0
            replaceNullsInNumericArrays(jsonResponse);

            // Extract relevant fields from the JSON response
            JsonArray strikes = jsonResponse.getJsonArray("strike");
            JsonArray expirations = jsonResponse.getJsonArray("expiration");
            JsonArray sides = jsonResponse.getJsonArray("side");
            JsonArray bids = jsonResponse.getJsonArray("bid");
            JsonArray asks = jsonResponse.getJsonArray("ask");
            JsonArray mids = jsonResponse.getJsonArray("mid");
            JsonArray deltas = jsonResponse.getJsonArray("delta");
            JsonArray gammas = jsonResponse.getJsonArray("gamma");
            JsonArray thetas = jsonResponse.getJsonArray("theta");
            JsonArray vegas = jsonResponse.getJsonArray("vega");
            JsonArray openInterests = jsonResponse.getJsonArray("openInterest");
            JsonArray volumes = jsonResponse.getJsonArray("volume");
            JsonArray intrinsicValues = jsonResponse.getJsonArray("intrinsicValue");
            JsonArray extrinsicValues = jsonResponse.getJsonArray("extrinsicValue");
            JsonArray ivs = jsonResponse.getJsonArray("iv");
            JsonArray underlyingPrices = jsonResponse.getJsonArray("underlyingPrice");

            // Date format for expiration conversion
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            // Map to hold merged options by strike and expiration
            Map<String, JsonObject> optionMap = new HashMap<>();

            // Loop over all options and categorize them by strike and expiration
            for (int i = 0; i < strikes.size(); i++) {
                // Convert expiration timestamp to YYYY-MM-DD format
                Date expirationDate = new Date(expirations.getLong(i) * 1000); // Convert seconds to milliseconds
                String formattedExpiration = dateFormat.format(expirationDate);

                String key = formattedExpiration + "-" + strikes.getDouble(i); // Key by expiration-strike

                JsonObject existingEntry = optionMap.getOrDefault(key, new JsonObject()
                        .put("strike", strikes.getDouble(i))
                        .put("expiration", formattedExpiration)
                );

                double underlyingPrice = underlyingPrices.getDouble(i);

                if (sides.getString(i).equals("call")) {
                    // Calculate DEX and GEX for call options
                    double callDelta = deltas.getDouble(i);
                    double callGamma = gammas.getDouble(i);
                    double callTheta = thetas.getDouble(i);
                    double callVega = vegas.getDouble(i);
                    int callOpenInterest = openInterests.getInteger(i);
                    double callDex = callDelta * callOpenInterest * 100;
                    double callGex = callGamma * callOpenInterest * 100 * underlyingPrice;

                    // Add call-specific data
                    existingEntry
                            .put("call_bid", bids.getDouble(i))
                            .put("call_ask", asks.getDouble(i))
                            .put("call_mid", mids.getDouble(i))
                            .put("call_delta", callDelta)
                            .put("call_gamma", callGamma)
                            .put("call_theta", callTheta)
                            .put("call_vega", callVega)
                            .put("call_oi", callOpenInterest)
                            .put("call_volume", volumes.getInteger(i))
                            .put("call_iv", ivs.getDouble(i))
                            .put("call_intrinsic_value", intrinsicValues.getDouble(i))
                            .put("call_extrinsic_value", extrinsicValues.getDouble(i))
                            .put("call_dex", callDex)
                            .put("call_gex", callGex);
                } else if (sides.getString(i).equals("put")) {
                    // Calculate DEX and GEX for put options
                    double putDelta = deltas.getDouble(i);
                    double putGamma = gammas.getDouble(i);
                    double putTheta = thetas.getDouble(i);
                    double putVega = vegas.getDouble(i);
                    int putOpenInterest = openInterests.getInteger(i);
                    double putDex = putDelta * putOpenInterest * 100;
                    double putGex = putGamma * putOpenInterest * 100 * underlyingPrice;

                    // Add put-specific data
                    existingEntry
                            .put("put_bid", bids.getDouble(i))
                            .put("put_ask", asks.getDouble(i))
                            .put("put_mid", mids.getDouble(i))
                            .put("put_delta", putDelta)
                            .put("put_gamma", putGamma)
                            .put("put_theta", putTheta)
                            .put("put_vega", putVega)
                            .put("put_oi", putOpenInterest)
                            .put("put_volume", volumes.getInteger(i))
                            .put("put_iv", ivs.getDouble(i))
                            .put("put_intrinsic_value", intrinsicValues.getDouble(i))
                            .put("put_extrinsic_value", extrinsicValues.getDouble(i))
                            .put("put_dex", putDex)
                            .put("put_gex", putGex);
                }

                // Store the merged option data
                optionMap.put(key, existingEntry);
            }

            // Convert the map to JsonArray
            mergedArray.addAll(JsonArray.of(optionMap.values().toArray()));
        } catch (Exception e) {
            System.out.println(jsonResponse.encodePrettily());
        }
        return mergedArray;
    }

    private void replaceNullsInNumericArrays(JsonObject jsonObject) {
        jsonObject.forEach(entry -> {
            Object value = entry.getValue();

            if (value instanceof JsonArray array) {
                if (isNumericArray(array)) {
                    for (int i = 0; i < array.size(); i++) {
                        if (array.getValue(i) == null) {
                            array.set(i, 0); // Replace null with 0
                        }
                    }
                }
            }
        });
    }

    // Helper method to check if an array only contains numeric values (integers or floats)
    private boolean isNumericArray(JsonArray array) {
        for (Object element : array) {
            if (element != null && !(element instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    public static String convertUnixToDate(long unixTimestamp) {
        // Convert the Unix timestamp to LocalDate
        LocalDate date = Instant.ofEpochSecond(unixTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        // Format the date as YYYY-MM-DD
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
