package org.optionsql.broker.tws;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;
import org.optionsql.broker.tws.request.TwsRequestManager;
import org.optionsql.broker.tws.request.TwsSession;
import org.optionsql.broker.tws.task.HistIV;
import org.optionsql.broker.tws.task.MarketPrice;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class TwsService extends BaseService {

    private TwsSession twsSession;
    private String hostname;
    private int port;
    private int clientId;
    private TwsRequestManager twsRequestManager;

    public TwsService(String serviceName) {
        super(serviceName);
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
        try {
            twsSession = new TwsSession(getVertx(), getLogger());
            twsRequestManager = new TwsRequestManager(twsSession);
            getLogger().info("Initializing TWS connection to " + hostname + ":" + port);
            return twsSession.connect(hostname, port, clientId).thenRun(() ->
                    getLogger().info("Connected to TWS at " + hostname + ":" + port)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void listenForRequests() {
        EventBus eventBus = getEventBus();
        String listenAddress = getServiceConfig().getString("listen", "broker.request");

        eventBus.consumer(listenAddress + ".get_current_price", this::handleGetCurrentPriceRequest);
        eventBus.consumer(listenAddress + ".get_hist_iv", this::handleGetHistIVRequest);

        getLogger().info("TwsService is now listening on: " + listenAddress);
    }

    private void handleGetCurrentPriceRequest(Message<JsonObject> message) {
        String symbol = message.body().getString("symbol");
        if (symbol == null || symbol.isEmpty()) {
            message.fail(400, "Missing 'symbol' in request");
            return;
        }

        MarketPrice marketPriceRequest = new MarketPrice(twsRequestManager);
        marketPriceRequest.fetchMarketPrice(symbol).thenRun(() -> {
            JsonObject response = new JsonObject()
                    .put("symbol", symbol)
                    .put("current_price", marketPriceRequest.getCurrentPrice());
            message.reply(response);
        }).exceptionally(ex -> {
            message.fail(500, "Failed to fetch current price: " + ex.getMessage());
            return null;
        });
    }

    private void handleGetHistIVRequest(Message<JsonObject> message) {
        String symbol = message.body().getString("symbol");
        if (symbol == null || symbol.isEmpty()) {
            message.fail(400, "Missing 'symbol' in request");
            return;
        }

        HistIV histIVRequest = new HistIV(twsRequestManager);
        histIVRequest.fetchHistoricalIV(symbol).thenRun(() -> {
            JsonObject response = new JsonObject()
                    .put("symbol", symbol)
                    .put("iv_low", histIVRequest.getHistoricalLowIV())
                    .put("iv_high", histIVRequest.getHistoricalHighIV());
            message.reply(response);
        }).exceptionally(ex -> {
            message.fail(500, "Failed to fetch historical IV: " + ex.getMessage());
            return null;
        });
    }
}
