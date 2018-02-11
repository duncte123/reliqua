package com.github.natanbc.reliqua.limiter;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("unused")
public abstract class RateLimiter {
    /**
     * Callback to be notified about rate limits.
     *
     * <br>The routes given to the callbacks might be null in case of a global rate limit.
     */
    public interface Callback {
        /**
         * Called when an attempted request is rate limited. Might be called more than once per request
         */
        void requestRateLimited(@Nullable String route);

        /**
         * Called when the rate limit is reset.
         */
        void rateLimitReset(@Nullable String route);
    }

    /**
     * Queue a task to be handled at a future time, respecting rate limits.
     *
     * @param task Task to be executed.
     */
    public abstract void queue(@Nullable String route, @Nonnull Runnable task);

    /**
     * Get how many requests may still be done before the rate limit is hit and no more requests can be made.
     *
     * @return Remaining requests.
     */
    @Nonnegative
    @CheckReturnValue
    public abstract int getRemainingRequests(@Nullable String route);

    /**
     * Get how much time, in milliseconds, is left until the rate limit is reset.
     *
     * @return Remaining cooldown time.
     */
    @CheckReturnValue
    public abstract long getTimeUntilReset(@Nullable String route);

    /**
     * Creates a new rate limiter that does no handling of rate limits, useful for situations where few requests are made.
     *
     * <br>When using this method, you are responsible for handling rate limits.
     *
     * @return A direct rate limiter.
     */
    @Nonnull
    @CheckReturnValue
    public static RateLimiter directLimiter() {
        return new RateLimiter() {
            @Override
            public void queue(@Nullable String route, @Nonnull Runnable task) {
                task.run();
            }

            @Override
            public int getRemainingRequests(@Nullable String route) {
                return Integer.MAX_VALUE;
            }

            @Override
            public long getTimeUntilReset(@Nullable String route) {
                return 0;
            }
        };
    }
}
