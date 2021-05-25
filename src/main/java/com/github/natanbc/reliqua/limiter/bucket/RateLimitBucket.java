package com.github.natanbc.reliqua.limiter.bucket;

import okhttp3.Response;

import javax.annotation.Nonnull;

/**
 * Inspired from https://github.com/MinnDevelopment/discord-webhooks/blob/a8bfd6b7744bf790a6ea0b3bc653569f23c162c3/src/main/java/club/minnced/discord/webhook/WebhookClient.java#L630
 */
public class RateLimitBucket implements IBucket {
    public static final int RATE_LIMIT_CODE = 429;
    public long resetTime;
    public int remainingUses;
    public int limit = Integer.MAX_VALUE;

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
}
