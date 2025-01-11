package org.optionsql.util;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TestRequest {

    public static void sendTickerDataRequest(Vertx vertx, String tickerJsonPath) {
        try {
            // Read ticker.json
            String content = new String(Files.readAllBytes(Paths.get(tickerJsonPath)));
            JsonObject tickerJson = new JsonObject(content);

            // Access the "segments" object and flatten all tickers into a single JsonArray
            JsonObject segments = tickerJson.getJsonObject("segments");
            JsonArray tickers = new JsonArray();

            segments.forEach(entry -> {
                if (entry.getValue() instanceof JsonArray) {
                    tickers.addAll((JsonArray) entry.getValue());
                }
            });

            // Create request JSON
            JsonObject request = new JsonObject()
                    .put("action", "add_ticker_data")
                    .put("tickers", tickers);

            EventBus eventBus = vertx.eventBus();

            // Send request to broker.request
            eventBus.request("broker.request", request, reply -> {
                if (reply.succeeded()) {
                    System.out.println("Received reply: " + reply.result().body().toString());
                } else {
                    System.err.println("Failed to receive reply: " + reply.cause().getMessage());
                }
            });

        } catch (Exception e) {
            System.err.println("Error reading ticker.json or sending request: " + e.getMessage());
        }
    }
}
