package org.optionsql;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * Entry point for the OptionsQL application.
 */
public class OptionsQL {

    private static final Logger logger = Logger.getLogger(OptionsQL.class.getName());

    public static void main(String[] args) {
        configureLogging("config/logging.properties");

        logger.info("Starting OptionsQL application...");

        Vertx vertx = Vertx.vertx();

        // Load configuration from a file
        JsonObject config = loadConfiguration("config/optionsql.json");

        // Extract service order from the configuration
        JsonArray serviceOrder = config.getJsonArray("serviceorder");

        // Deploy services in order
        deployServicesInOrder(vertx, config, serviceOrder)
                .onSuccess(id -> logger.info("All services deployed successfully."))
                .onFailure(err -> logger.severe("Service deployment failed: " + err.getMessage()));
    }

    /**
     * Deploy services in the order specified by the configuration.
     *
     * @param vertx         the Vert.x instance
     * @param config        the application configuration
     * @param serviceOrder  the ordered array of service names
     * @return a Future representing the completion of all deployments
     */
    private static Future<Void> deployServicesInOrder(Vertx vertx, JsonObject config, JsonArray serviceOrder) {
        Future<Void> future = Future.succeededFuture();

        for (Object serviceName : serviceOrder) {
            future = future.compose(ignored -> deployService(vertx, config, (String) serviceName).mapEmpty());
        }

        return future;
    }

    /**
     * Deploy a specific service based on the configuration.
     *
     * @param vertx        the Vert.x instance
     * @param config       the application configuration
     * @param serviceName  the name of the service to deploy
     * @return a Future representing the deployment result
     */
    private static Future<String> deployService(Vertx vertx, JsonObject config, String serviceName) {
        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        String serviceClass = config.getJsonObject("services").getJsonObject(serviceName).getString("class");

        logger.info("Deploying service: " + serviceName + " (" + serviceClass + ")");
        try {
            Class<?> clazz = Class.forName(serviceClass);
            return vertx.deployVerticle(clazz.getName(), options)
                    .onSuccess(id -> logger.info(serviceName + " deployed successfully with ID: " + id))
                    .onFailure(err -> logger.severe("Failed to deploy " + serviceName + ": " + err.getMessage()));
        } catch (ClassNotFoundException e) {
            logger.severe("Service class not found: " + serviceClass);
            return Future.failedFuture(e);
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

    /**
     * Custom formatter for logging that excludes method names.
     */
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
