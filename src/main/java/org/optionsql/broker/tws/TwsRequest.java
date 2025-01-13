package org.optionsql.broker.tws;

@FunctionalInterface
public interface TwsRequest {
    void execute(TwsSession session, Runnable onComplete);

    // Default method for logging (optional)
    default String getDescription() {
        return "Generic TWS Request";
    }
}
