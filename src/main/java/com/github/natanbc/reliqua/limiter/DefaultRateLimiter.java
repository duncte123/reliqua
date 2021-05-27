package com.github.natanbc.reliqua.limiter;

import com.github.natanbc.reliqua.Reliqua;
import com.github.natanbc.reliqua.limiter.factory.RateLimiterFactory;
import okhttp3.Response;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

public class DefaultRateLimiter extends RateLimiter {
    public long resetTime;
    public int remainingUses;
    public int limit = Integer.MAX_VALUE;

    protected final Reliqua api;
    protected final BlockingQueue<LimiterPair> pendingRequests = new LinkedBlockingQueue<>();
    protected final ScheduledExecutorService executor;
    protected boolean isQueued = false;

    /**
     * Creates a new rate limiter.
     *
     * @param api The current api instance
     * @param executor The executor to schedule cooldowns and rate limit processing.
     */
    public DefaultRateLimiter(Reliqua api, ScheduledExecutorService executor) {
        this.api = api;
        this.executor = executor;
    }

    public DefaultRateLimiter(Reliqua api, String key) {
        this(api, Executors.newSingleThreadScheduledExecutor((r) -> new Thread(r, "Reliqua ratelimiter: " + key)));
    }

    @Override
    public void queue(@Nonnull LimiterPair task) {
        final boolean wasQueued = isQueued;
        isQueued = true;

        pendingRequests.add(task);

        if (!wasQueued) {
            backoffQueue();
        }
    }

    @Override
    public synchronized boolean isRateLimit() {
        if (retryAfter() <= 0) {
            remainingUses = limit;
        }

        return remainingUses <= 0;
    }

    @Override
    public synchronized long retryAfter() {
        return resetTime - System.currentTimeMillis();
    }

    @Override
    public void backoffQueue() {
        final long delay = retryAfter();
        executor.schedule(this::drainQueue, delay, TimeUnit.MILLISECONDS);
    }

    protected synchronized void drainQueue() {
        boolean graceful = true;
        while (!pendingRequests.isEmpty()) {
            final LimiterPair r = pendingRequests.peek();
            graceful = handle(r);

            if (!graceful) {
                break;
            }
        }

        isQueued = !graceful;

        if (this.api.isShutdown() && graceful) {
            executor.shutdown();
        }
    }

    @Override
    public void close() {
        if (this.pendingRequests.isEmpty()) {
            this.executor.shutdown();
        }
    }

    @Override
    public int getRemainingRequests() {
        return this.remainingUses;
    }

    @Override
    public long getTimeUntilReset() {
        return this.resetTime;
    }

    protected boolean handle(LimiterPair pair) {
        if (pair.getRequest().future.isDone()) {
            pendingRequests.poll();
            return true;
        }

        pair.getRunnable().run();

        return !this.isRateLimit();
    }

    private synchronized void handleRatelimit(Response response, long current) {
        final String retryAfter = response.header("Retry-After");
        final String limitHeader = response.header("X-RateLimit-Limit", "5");
        long delay;

        if (retryAfter == null) { // this should never happen
            delay = 30000; // 30 seconds as fallback
        } else {
            delay = Long.parseLong(retryAfter) * 1000;
        }

        // LOG.error("Encountered 429, retrying after {} ms", delay);
        resetTime = current + delay;
        remainingUses = 0;
        //noinspection ConstantConditions
        limit = Integer.parseInt(limitHeader);
    }

    private synchronized void update0(Response response) {
        final long current = System.currentTimeMillis();
        final boolean is429 = response.code() == RATE_LIMIT_CODE;
        final String remainingHeader = response.header("X-RateLimit-Remaining");
        final String limitHeader = response.header("X-RateLimit-Limit");
        final String resetHeader = response.header("X-RateLimit-Reset-After");

        if (is429) {
            handleRatelimit(response, current);
            return;
            // TODO: add logging?
        } else if (remainingHeader == null || limitHeader == null || resetHeader == null) {
            // LOG.debug("Failed to update buckets due to missing headers in response with code: {} and headers: \n{}", response.code(), response.headers());
            return;
        }

        remainingUses = Integer.parseInt(remainingHeader);
        limit = Integer.parseInt(limitHeader);
        final long reset = (long) Math.ceil(Double.parseDouble(resetHeader)); // relative seconds
        final long delay = reset * 1000;
        resetTime = current + delay;
    }

    @Override
    public void update(@Nonnull Response response) {
        update0(response);
    }

    public static class Factory extends RateLimiterFactory {
        private final Reliqua api;

        public Factory(Reliqua api) {
            this.api = api;
        }

        @Override
        protected RateLimiter createRateLimiter(String key) {
            return new DefaultRateLimiter(api, key);
        }
    }
}
