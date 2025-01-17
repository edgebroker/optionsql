package org.optionsql.base;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseService extends AbstractVerticle {

    private final Logger logger;

    private final String serviceName;
    private JsonObject globalConfig;
    private JsonObject serviceConfig;

    protected BaseService(String serviceName) {
        this.logger = Logger.getLogger(getClass().getName());
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void start() throws Exception {
        // Load the global configuration
        this.globalConfig = config();
        if (globalConfig == null) {
            throw new IllegalStateException("Global configuration is missing.");
        }

        // Load the service-specific configuration
        this.serviceConfig = globalConfig.getJsonObject("services").getJsonObject(serviceName);
        if (serviceConfig == null) {
            throw new IllegalStateException("Service-specific configuration for '" + serviceName + "' is missing.");
        }

        if (isDebugEnabled()) {
            logger.info("BaseService started for service: " + serviceName);
        }
    }

    /**
     * Get the Vert.x execution context.
     * @return the Vert.x context
     */
    protected Context getVertxContext() {
        return vertx.getOrCreateContext();
    }

    /**
     * Get the Vert.x event bus.
     * @return the Vert.x event bus
     */
    protected EventBus getEventBus() {
        return vertx.eventBus();
    }

    /**
     * Get the global configuration.
     * @return the global configuration as a JsonObject
     */
    protected JsonObject getGlobalConfig() {
        return globalConfig;
    }

    /**
     * Get the service-specific configuration.
     * @return the service-specific configuration as a JsonObject
     */
    protected JsonObject getServiceConfig() {
        return serviceConfig;
    }

    protected String getFutureDateISO8601(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        String date = String.format("%1$tY-%1$tm-%1$td", calendar);
        if (isDebugEnabled()) {
            logger.info("Calculated future date: " + date);
        }
        return date;
    }

    protected Future<JsonObject> makeHttpRequest(String urlStr) {
        if (isDebugEnabled()) {
            logger.info("Making HTTP request to URL: " + urlStr);
        }

        WebClientOptions options = new WebClientOptions()
                .setDefaultHost("api.marketdata.app")
                .setDefaultPort(443) // Assuming HTTPS
                .setSsl(true); // Enable SSL for HTTPS

        WebClient client = WebClient.create(vertx, options);

        // Parse the URL for path and query params
        String path = urlStr.substring(urlStr.indexOf("/v1"));

        return client
                .get(path)
                .send()
                .compose(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 203) {
                        JsonObject jsonResponse = response.bodyAsJsonObject();
                        if (isDebugEnabled()) {
                            logger.info("Received response: " + jsonResponse.encodePrettily());
                        }
                        return Future.succeededFuture(jsonResponse);
                    } else {
                        return Future.failedFuture(new RuntimeException("HTTP request failed with status: " + response.statusCode()));
                    }
                })
                .recover(err -> {
                    // Handle failures gracefully
                    JsonObject fallbackResponse = new JsonObject()
                            .put("s", "error")
                            .put("message", err.getMessage());
                    return Future.succeededFuture(fallbackResponse);
                });
    }

    protected Future<Void> processSqlFile(Connection connection, String filePath) {
        return vertx.executeBlocking(promise -> {
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    promise.fail(new IllegalArgumentException("SQL file does not exist: " + filePath));
                    return;
                }

                String sqlContent = Files.readString(path);
                try (Statement statement = connection.createStatement()) {
                    for (String sql : sqlContent.split(";")) { // Split on ';' for individual statements
                        if (!sql.trim().isEmpty()) {
                            statement.execute(sql.trim());
                        }
                    }
                    promise.complete();
                }
            } catch (Exception e) {
                getLogger().severe("Failed to process SQL file: " + filePath + " - " + e.getMessage());
                promise.fail(e);
            }
        });
    }

    /**
     * Check if debug logging is enabled.
     * @return true if debug logging is enabled, false otherwise
     */
    protected boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }
}
