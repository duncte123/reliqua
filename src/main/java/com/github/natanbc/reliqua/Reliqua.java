package com.github.natanbc.reliqua;

import com.github.natanbc.reliqua.limiter.RateLimiter;
import com.github.natanbc.reliqua.request.PendingRequest;
import com.github.natanbc.reliqua.util.RequestMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * Used to create REST API wrappers, providing a rate limiter and easy way to have both synchronous and asynchronous
 * requests with a common return type, leaving it up to the user to choose the method.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class Reliqua {
    private final RateLimiter rateLimiter;
    private final OkHttpClient client;
    private boolean trackCallSites;

    /**
     * Creates a new reliqua instance.
     *
     * @param rateLimiter RateLimiter used to throttle requests. Defaults to a direct (unthrottled) implementation if null.
     * @param client The OkHttpClient used to make HTTP requests. May not be null.
     * @param trackCallSites Whether or not call sites should be tracked for async requests.
     */
    protected Reliqua(RateLimiter rateLimiter, OkHttpClient client, boolean trackCallSites) {
        if(rateLimiter == null) {
            rateLimiter = RateLimiter.directLimiter();
        }
        if(client == null) {
            throw new IllegalArgumentException("Client is null");
        }
        this.rateLimiter = rateLimiter;
        this.client = client;
        this.trackCallSites = trackCallSites;
    }

    /**
     * Creates a new reliqua with no rate limiter and with call site tracking disabled.
     *
     * @param client The OkHttpClient used to make HTTP requests. May not be null.
     */
    protected Reliqua(OkHttpClient client) {
        this(null, client, false);
    }

    /**
     * Creates a new reliqua with no rate limiter and with call site tracking disabled.
     */
    protected Reliqua() {
        this(null, new OkHttpClient(), false);
    }

    /**
     * Returns the {@link OkHttpClient http client} used for making requests
     *
     * @return the client
     */
    @CheckReturnValue
    @Nonnull
    public OkHttpClient getClient() {
        return client;
    }

    /**
     * Enable or disable call site tracking.
     *
     * @param trackCallSites true to track call sites
     */
    public void setTrackCallSites(boolean trackCallSites) {
        this.trackCallSites = trackCallSites;
    }

    /**
     * Returns whether or not async requests track call site
     *
     * @return true if call site tracking is enabled
     */
    @CheckReturnValue
    public boolean isTrackingCallSites() {
        return trackCallSites;
    }

    /**
     * Returns this instance's RateLimiter, used to throttle requests.
     *
     * @return This instance's RateLimiter.
     */
    @CheckReturnValue
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    protected <T> PendingRequest<T> createRequest(String route, Request request, RequestMapper<T> mapper) {
        Objects.requireNonNull(route, "Route may not be null");
        Objects.requireNonNull(request, "Request may not be null");
        Objects.requireNonNull(mapper, "Mapper may not be null");
        return new PendingRequest<T>(this, request, route) {
            @Nullable
            @Override
            protected T mapData(@Nonnull ResponseBody response) throws IOException {
                return mapper.apply(response);
            }
        };
    }

    protected <T> PendingRequest<T> createRequest(String route, Request.Builder requestBuilder, RequestMapper<T> mapper) {
        Objects.requireNonNull(requestBuilder, "Request builder may not be null");
        return createRequest(route, requestBuilder.build(), mapper);
    }
}
