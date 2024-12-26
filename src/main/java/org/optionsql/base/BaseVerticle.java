package org.optionsql.base;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

public abstract class BaseVerticle extends AbstractVerticle {

    private final JsonObject eventBusConfig;

    protected BaseVerticle(JsonObject config) {
        this.eventBusConfig = config.getJsonObject("eventbus");
    }

    @Override
    public void start() throws Exception {
        // Register handlers for global event bus addresses
        vertx.eventBus().consumer(eventBusConfig.getString("available"), message -> onAvailable((JsonObject) message.body()));
        vertx.eventBus().consumer(eventBusConfig.getString("unavailable"), message -> onUnavailable((JsonObject) message.body()));
        vertx.eventBus().consumer(eventBusConfig.getString("sleep"), message -> onSleep((JsonObject) message.body()));
        vertx.eventBus().consumer(eventBusConfig.getString("awake"), message -> onAwake((JsonObject) message.body()));
    }

    /**
     * Handle "service.available" messages.
     * @param payload the incoming message payload
     */
    protected abstract void onAvailable(JsonObject payload);

    /**
     * Handle "service.unavailable" messages.
     * @param payload the incoming message payload
     */
    protected abstract void onUnavailable(JsonObject payload);

    /**
     * Handle "service.sleep" messages.
     * @param payload the incoming message payload
     */
    protected abstract void onSleep(JsonObject payload);

    /**
     * Handle "service.awake" messages.
     * @param payload the incoming message payload
     */
    protected abstract void onAwake(JsonObject payload);

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
}
