package com.github.natanbc.reliqua.limiter.bucket;

import okhttp3.Response;

import javax.annotation.Nonnull;

public class DirectBucket implements IBucket {
    @Override
    public boolean isRateLimit() {
        return false;
    }

    @Override
    public long retryAfter() {
        return 0;
    }

    @Override
    public void update(@Nonnull Response response) {
        // nothing here
    }
}
