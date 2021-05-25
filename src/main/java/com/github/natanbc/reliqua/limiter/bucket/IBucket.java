package com.github.natanbc.reliqua.limiter.bucket;

import okhttp3.Response;

import javax.annotation.Nonnull;

public interface IBucket {
    boolean isRateLimit();
    long retryAfter();
    void update(@Nonnull Response response);
}
