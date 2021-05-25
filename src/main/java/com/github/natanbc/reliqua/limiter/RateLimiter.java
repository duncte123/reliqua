package com.github.natanbc.reliqua.limiter;

import okhttp3.Response;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

public abstract class RateLimiter {
    public static final int RATE_LIMIT_CODE = 429;

    /**
     * Callback to be notified about rate limits.
     *
     * <br>The routes given to the callbacks might be null in case of a global rate limit.
     *
     * @deprecated this is not used anywhere in the code
     */
    @Deprecated
    public interface Callback {
        /**
         * Called when an attempted request is rate limited. Might be called more than once per request
         */
        void requestRateLimited();

        /**
         * Called when the rate limit is reset.
         */
        void rateLimitReset();
    }

    /**
     * Queue a task to be handled at a future time, respecting rate limits.
     *
     * @param task Task to be executed.
     */
    public abstract void queue(@Nonnull LimiterPair task);

    /**
     * Get how many requests may still be done before the rate limit is hit and no more requests can be made.
     *
     * @return Remaining requests.
     */
    @Nonnegative
    @CheckReturnValue
    public abstract int getRemainingRequests();

    /**
     * Get how much time, in milliseconds, is left until the rate limit is reset.
     *
     * @return Remaining cooldown time.
     */
    @CheckReturnValue
    public abstract long getTimeUntilReset();

    /**
     * Returns if the request was rate-limited
     *
     * @return true if the request was rate-limited
     */
    public abstract boolean isRateLimit();

    /**
     * Returns the value of the retry-after header in seconds
     *
     * @return The value of the retry-after header in seconds
     */
    public abstract long retryAfter();

    /**
     * Updates the bucket with the latest rate limit headers
     *
     * @param response The response with the headers
     */
    public abstract void update(@Nonnull Response response);

    /**
     * Calls a backoff to the queue and forces it to wait with the next request
     */
    public abstract void backoffQueue();

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
        return DirectLimiter.INSTANCE;
    }

    private static class DirectLimiter extends RateLimiter {
        static final DirectLimiter INSTANCE = new DirectLimiter();

        private DirectLimiter() {}

        @Override
        public void queue(@Nonnull LimiterPair task) {
            task.getRunnable().run();
        }

        @Override
        public void backoffQueue() {}

        @Override
        public boolean isRateLimit() {
            return false;
        }

        @Override
        public long retryAfter() {
            return 0;
        }

        @Override
        public void update(@Nonnull Response response) {}

        @Override
        public int getRemainingRequests() {
            return Integer.MAX_VALUE;
        }

        @Override
        public long getTimeUntilReset() {
            return 0;
        }
    }
}
