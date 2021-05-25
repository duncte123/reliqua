package com.github.natanbc.reliqua.limiter;

import com.github.natanbc.reliqua.request.PendingRequest;

/**
 * Represents a class with all the data that a rate limiter needs
 */
public class LimiterPair {
    private final PendingRequest<?> request;
    private final Runnable runnable;

    public LimiterPair(PendingRequest<?> request, Runnable runnable) {
        this.request = request;
        this.runnable = runnable;
    }

    /**
     * Returns the request that we are about to execute
     *
     * @return The request that we are about to execute
     */
    public PendingRequest<?> getRequest() {
        return request;
    }

    /**
     * Returns the runnable that executes the request
     *
     * @return The runnable that executes the request to the server
     */
    public Runnable getRunnable() {
        return runnable;
    }
}
