package com.github.natanbc.reliqua.limiter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter that applies to all routes, global to the whole REST API.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class GlobalRateLimiter extends RateLimiter {
    protected final Queue<Runnable> pendingRequests = new ConcurrentLinkedQueue<>();
    protected final AtomicInteger requestsDone = new AtomicInteger(0);
    protected final AtomicLong ratelimitResetTime = new AtomicLong();
    protected final ScheduledExecutorService executor;
    protected final Callback callback;
    protected final int maxRequests;
    protected final long cooldownMillis;
    protected Future<?> ratelimitTimeResetFuture;

    /**
     * Creates a new global rate limiter.
     *
     * @param executor The executor to schedule cooldowns and rate limit processing.
     * @param callback Callback to be notified about rate limiter actions.
     * @param maxRequests Maximum amount of requests that may be done before being rate limited.
     * @param cooldownMillis Time to reset the requests done, starting from the first request done since the last reset.
     */
    public GlobalRateLimiter(ScheduledExecutorService executor, Callback callback, int maxRequests, long cooldownMillis) {
        this.executor = executor;
        this.callback = callback;
        this.maxRequests = maxRequests;
        this.cooldownMillis = cooldownMillis;
    }

    @Override
    public void queue(@Nullable String route, @Nonnull Runnable task) {
        pendingRequests.offer(task);
        executor.execute(this::process);
    }

    @Override
    public int getRemainingRequests(@Nullable String route) {
        return maxRequests - requestsDone.get();
    }

    @Override
    public long getTimeUntilReset(@Nullable String route) {
        return TimeUnit.NANOSECONDS.toMillis(ratelimitResetTime.get() - System.nanoTime());
    }

    private void process() {
        Runnable r = pendingRequests.peek();
        if(r == null) return;
        if(requestsDone.get() >= maxRequests) {
            executor.schedule(this::process, ratelimitResetTime.get() - System.nanoTime(), TimeUnit.NANOSECONDS);
            if(callback != null) {
                try {
                    callback.requestRateLimited(null);
                } catch(Exception ignored) {}
            }
            return;
        }
        if(requestsDone.incrementAndGet() >= maxRequests) {
            synchronized(this) {
                if(ratelimitTimeResetFuture != null) {
                    executor.schedule(this::process, ratelimitResetTime.get() - System.nanoTime(), TimeUnit.NANOSECONDS);
                    if(callback != null) {
                        try {
                            callback.requestRateLimited(null);
                        } catch(Exception ignored) {}
                    }
                    return;
                }
                ratelimitTimeResetFuture = executor.schedule(()->{
                    ratelimitResetTime.set(0);
                    requestsDone.set(0);
                    ratelimitTimeResetFuture = null;
                    if(callback != null) {
                        try {
                            callback.rateLimitReset(null);
                        } catch(Exception ignored) {}
                    }
                }, cooldownMillis, TimeUnit.MILLISECONDS);
                executor.schedule(this::process, cooldownMillis, TimeUnit.MILLISECONDS);
                return;
            }
        }
        pendingRequests.poll().run();
        ratelimitResetTime.compareAndSet(0, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cooldownMillis));
    }
}
