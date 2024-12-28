package org.optionsql.orchestrate;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseVerticle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrchestratorVerticle extends BaseVerticle {

    private final Set<String> availableServices = new HashSet<>();
    private List<String> serviceOrder;

    public OrchestratorVerticle() {
        super("orchestrate");
    }

    @Override
    protected void onSleep() {
        getLogger().info("Orchestrator service is going to sleep...");
    }

    @Override
    protected void onAwake() {
        getLogger().info("Orchestrator service is waking up...");
    }

    @Override
    public void start() throws Exception {
        super.start();

        Vertx vertx = getVertxContext().owner();
        String availableAddress = getEventBusConfig().getString("available");
        String awakeAddress = getEventBusConfig().getString("awake");

        // Load service order from configuration
        serviceOrder = getGlobalConfig().getJsonArray("serviceorder").getList();
        getLogger().info("Service order: " + serviceOrder);

        // Listen for availability announcements
        getEventBus().consumer(availableAddress, message -> {
            JsonObject body = (JsonObject) message.body();
            String service = body.getString("service");

            if (!serviceOrder.contains(service)) {
                getLogger().warning("Unknown service reported availability: " + service);
                return;
            }

            availableServices.add(service);
            getLogger().info(service + " service is now available.");

            // Check if all services are available
            if (availableServices.containsAll(serviceOrder)) {
                getLogger().info("All services are available. Sending awake messages.");
                wakeUpAllServices(awakeAddress);
            }
        });

        getLogger().info("OrchestratorVerticle started and waiting for service availability.");
        notifyAvailable();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        notifyAvailable();
    }

    /**
     * Sends awake messages to all services in the service order.
     *
     * @param awakeAddress the address to publish the awake message
     */
    private void wakeUpAllServices(String awakeAddress) {
        for (String serviceName : serviceOrder) {
            JsonObject awakeMessage = new JsonObject().put("service", serviceName);
            getEventBus().publish(awakeAddress, awakeMessage);
            getLogger().info("Sent awake message to " + serviceName + " service.");
        }
    }
}
