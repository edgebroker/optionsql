package org.optionsql;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import org.optionsql.fetch.FetchVerticle;
import org.optionsql.orchestrate.OrchestratorVerticle;
import org.optionsql.store.StoreVerticle;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.*;

public class OptionsQL {

    private static final Logger logger = Logger.getLogger(OptionsQL.class.getName());

    public static void main(String[] args) {
        configureLogging("config/logging.properties");

        logger.info("Starting OptionsQL application...");

        Vertx vertx = Vertx.vertx();

        // Load configuration from a file
        JsonObject config = loadConfiguration("config/optionsql.json");

        // Extract service order from the configuration
        List<String> serviceOrder = config.getJsonArray("serviceorder").getList();

        // Deploy services in order
        deployServicesInOrder(vertx, config, serviceOrder)
                .onSuccess(id -> logger.info("All services deployed successfully."))
                .onFailure(err -> logger.severe("Service deployment failed: " + err.getMessage()));
    }

    private static Future<Void> deployServicesInOrder(Vertx vertx, JsonObject config, List<String> serviceOrder) {
        Future<Void> future = Future.succeededFuture();

        for (String serviceName : serviceOrder) {
            future = future.compose(ignored -> deployService(vertx, config, serviceName).mapEmpty());
        }

        return future;
    }

    private static Future<String> deployService(Vertx vertx, JsonObject config, String serviceName) {
        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        switch (serviceName) {
            case "orchestrate":
                logger.info("Deploying OrchestratorVerticle...");
                return vertx.deployVerticle(OrchestratorVerticle.class, options)
                        .onSuccess(id -> logger.info("OrchestratorVerticle deployed successfully with ID: " + id))
                        .onFailure(err -> logger.severe("Failed to deploy OrchestratorVerticle: " + err.getMessage()));

            case "fetch":
                logger.info("Deploying FetchVerticle...");
                return vertx.deployVerticle(FetchVerticle.class, options)
                        .onSuccess(id -> logger.info("FetchVerticle deployed successfully with ID: " + id))
                        .onFailure(err -> logger.severe("Failed to deploy FetchVerticle: " + err.getMessage()));

            case "store":
                logger.info("Deploying StoreVerticle...");
                return vertx.deployVerticle(StoreVerticle.class, options)
                        .onSuccess(id -> logger.info("StoreVerticle deployed successfully with ID: " + id))
                        .onFailure(err -> logger.severe("Failed to deploy StoreVerticle: " + err.getMessage()));

            default:
                logger.severe("Unknown service: " + serviceName);
                return Future.failedFuture("Unknown service: " + serviceName);
        }
    }

    /**
     * Configures logging using the specified properties file.
     *
     * @param configPath the path to the logging configuration file
     */
    private static void configureLogging(String configPath) {
        Path logsDir = Paths.get("logs");
        try {
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            try (FileInputStream configStream = new FileInputStream(configPath)) {
                LogManager.getLogManager().readConfiguration(configStream);
            }
            // Apply custom formatter to all handlers
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                handler.setFormatter(new NoMethodNameFormatter());
            }
        } catch (IOException e) {
            System.err.println("Failed to configure logging from " + configPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads the application configuration from a JSON file.
     *
     * @param configPath the path to the configuration file
     * @return the loaded JsonObject
     */
    private static JsonObject loadConfiguration(String configPath) {
        try (FileInputStream configStream = new FileInputStream(configPath)) {
            byte[] data = configStream.readAllBytes();
            String jsonString = new String(data);
            return new JsonObject(jsonString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class NoMethodNameFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return String.format(
                    "%1$tF | %1$tT | %2$s | %3$s | %4$s%n",
                    record.getMillis(),
                    record.getLevel(),
                    record.getLoggerName(),
                    record.getMessage()
            );
        }
    }
}
