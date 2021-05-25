package com.github.natanbc.reliqua.limiter;

import com.github.natanbc.reliqua.Reliqua;
import com.github.natanbc.reliqua.limiter.bucket.IBucket;
import com.github.natanbc.reliqua.limiter.bucket.RateLimitBucket;
import com.github.natanbc.reliqua.limiter.factory.RateLimiterFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

public class DefaultRateLimiter extends RateLimiter {
    protected final RateLimitBucket bucket = new RateLimitBucket();
    protected final Reliqua api;
    protected final BlockingQueue<LimiterPair> pendingRequests = new LinkedBlockingQueue<>();
    protected final ScheduledExecutorService executor;
    protected boolean isQueued;

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
        this(api, Executors.newSingleThreadScheduledExecutor((r) -> {
            final Thread t = new Thread(r, "Reliqua ratelimiter: " + key);
            t.setDaemon(true);
            return t;
        }));
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
    public void backoff() {
        this.backoffQueue();
    }

    protected void backoffQueue() {
        final long delay = bucket.retryAfter();
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
    public IBucket getBucket() {
        return bucket;
    }

    @Override
    public int getRemainingRequests() {
        return this.bucket.remainingUses;
    }

    @Override
    public long getTimeUntilReset() {
        return this.bucket.resetTime;
    }

    protected boolean handle(LimiterPair pair) {
        if (pair.getRequest().future.isDone()) {
            pendingRequests.poll();
            return true;
        }

        pair.getRunnable().run();

        return !this.bucket.isRateLimit();
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
