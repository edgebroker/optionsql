package org.optionsql.broker.tws.request;

import java.util.LinkedList;
import java.util.Queue;

public class TwsRequestManager {

    private final TwsSession twsSession;

    private final Queue<TwsRequest> waitingQueue = new LinkedList<>();
    private final Queue<TwsRequest> pendingQueue = new LinkedList<>();

    private static final int MAX_CONCURRENT_REQUESTS = 50;

    public TwsRequestManager(TwsSession twsSession) {
        this.twsSession = twsSession;
    }

    public void enqueueRequest(TwsRequest request) {
        waitingQueue.add(request);
        twsSession.getLogger().info("Enqueued request: " + request.getDescription());
        tryDispatchNext();
    }

    private void tryDispatchNext() {
        while (pendingQueue.size() < MAX_CONCURRENT_REQUESTS && !waitingQueue.isEmpty()) {
            TwsRequest nextRequest = waitingQueue.poll();
            pendingQueue.add(nextRequest);
            sendRequest(nextRequest);
        }
    }

    private void sendRequest(TwsRequest request) {
        try {
            twsSession.getLogger().info("Sending request: " + request.getDescription());
            request.execute(twsSession, () -> {
                twsSession.getLogger().info("Completed request: " + request.getDescription());
                onRequestComplete(request);
            });
        } catch (Exception e) {
            twsSession.getLogger().severe("Error sending request: " + request.getDescription() + ", exception=" + e);
            onRequestComplete(request);  // Ensure the queue moves on
        }
    }

    public void onRequestComplete(TwsRequest request) {
        if (pendingQueue.remove(request)) {
            tryDispatchNext();
        }
    }
}
