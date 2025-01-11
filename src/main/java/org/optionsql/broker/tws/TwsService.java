package org.optionsql.broker.tws;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class TwsService extends BaseService {

    private final TwsSession twsSession;
    private String hostname;
    private int port;
    private int clientId;

    public TwsService(String serviceName) {
        super(serviceName);
        twsSession = new TwsSession(getVertx(), getLogger());
    }

    @Override
    public void start() throws Exception {
        super.start();

        JsonObject serviceConfig = getServiceConfig();
        hostname = serviceConfig.getString("hostname", "127.0.0.1");
        port = serviceConfig.getInteger("port", 7496);
        clientId = serviceConfig.getInteger("clientid", 0);

        initializeTwsConnection()
                .thenRun(this::listenForRequests)
                .exceptionally(ex -> {
                    getLogger().log(Level.SEVERE, "Failed to connect to TWS", ex);
                    return null;
                });
    }

    private CompletableFuture<Void> initializeTwsConnection() {
        getLogger().info("Initializing TWS connection to " + hostname + ":" + port);
        return twsSession.connect(hostname, port, clientId).thenRun(() ->
                getLogger().info("Connected to TWS at " + hostname + ":" + port)
        );
    }

    private void listenForRequests() {
        EventBus eventBus = getEventBus();
        String listenAddress = getServiceConfig().getString("listen", "broker.request");

        eventBus.consumer(listenAddress, this::handleRequest);
        getLogger().info("TwsService is now listening on: " + listenAddress);
    }

    private void handleRequest(Message<JsonObject> message) {
        JsonObject request = message.body();
        getLogger().info("Received request: " + request.encodePrettily());

        String action = request.getString("action");
        if ("add_ticker_data".equalsIgnoreCase(action)) {
            addTickerData(request, message);
        } else {
            message.fail(400, "Unknown action: " + action);
        }
    }

    private void addTickerData(JsonObject request, Message<JsonObject> message) {
        JsonArray tickers = request.getJsonArray("tickers");
        JsonObject response = new JsonObject();

        CompletableFuture<?>[] futures = tickers.stream().map(tickerObj -> {
            String symbol = (String) tickerObj;

            HistIV histIVRequest = new HistIV(twsSession);
            MarketPrice marketPriceRequest = new MarketPrice(twsSession);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(
                    histIVRequest.fetchHistoricalIV(symbol),
                    marketPriceRequest.fetchMarketPrice(symbol)
            ).thenAccept(v -> response.put(symbol, new JsonObject()
                    .put("current_price", marketPriceRequest.getCurrentPrice())
                    .put("iv_low", histIVRequest.getHistoricalLowIV())
                    .put("iv_high", histIVRequest.getHistoricalHighIV())));

            return combinedFuture;
        }).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).thenRun(() -> {
            message.reply(response);
            getLogger().info("Replied with ticker data: " + response.encodePrettily());
        }).exceptionally(ex -> {
            message.fail(500, "Failed to fetch ticker data: " + ex.getMessage());
            getLogger().severe("Error fetching ticker data: " + ex.getMessage());
            return null;
        });
    }
}