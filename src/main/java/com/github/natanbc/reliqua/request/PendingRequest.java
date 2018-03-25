package com.github.natanbc.reliqua.request;

import com.github.natanbc.reliqua.Reliqua;
import com.github.natanbc.reliqua.limiter.RateLimiter;
import com.github.natanbc.reliqua.util.StatusCodeValidator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * This class represents a request which has not yet been scheduled to execute.
 *
 * <br>The request is only executed when {@link #execute() execute}, {@link #async(Consumer, Consumer) async} or
 * {@link #submit() submit} are called.
 *
 * <br>This request may be executed more than once.
 *
 * This class was inspired by <a href="https://github.com/DV8FromTheWorld/JDA">JDA</a>'s
 * <a href="https://github.com/DV8FromTheWorld/JDA/blob/907f766537a18b610ed8a2cedf95cf6754cf50ee/src/main/java/net/dv8tion/jda/core/requests/RestAction.java">RestAction</a> class.
 *
 * @param <T> The type of object returned by this request.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class PendingRequest<T> {
    private final Reliqua api;
    private final Request httpRequest;
    private final StatusCodeValidator statusCodeValidator;
    private RateLimiter rateLimiter;

    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request httpRequest, @Nullable StatusCodeValidator statusCodeValidator) {
        this.api = Objects.requireNonNull(api, "API may not be null");
        this.rateLimiter = rateLimiter;
        this.httpRequest = Objects.requireNonNull(httpRequest, "HTTP request may not be null");
        this.statusCodeValidator = statusCodeValidator == null ? ignored->true : statusCodeValidator;
    }

    @Deprecated
    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request httpRequest, @Nullable IntPredicate statusCodeValidator) {
        this(api, rateLimiter, httpRequest, StatusCodeValidator.wrap(statusCodeValidator));
    }

    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request httpRequest, @Nullable StatusCodeValidator statusCodeValidator) {
        this(api, null, httpRequest, statusCodeValidator);
    }

    @Deprecated
    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request httpRequest, @Nullable IntPredicate statusCodeValidator) {
        this(api, httpRequest, StatusCodeValidator.wrap(statusCodeValidator));
    }

    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request httpRequest) {
        this(api, rateLimiter, httpRequest, null);
    }

    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request httpRequest) {
        this(api, httpRequest, null);
    }

    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request.Builder httpRequestBuilder, @Nullable StatusCodeValidator statusCodeValidator) {
        this(
                api,
                rateLimiter,
                Objects.requireNonNull(httpRequestBuilder, "HTTP request builder may not be null").build(),
                statusCodeValidator
        );
    }

    @Deprecated
    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request.Builder httpRequestBuilder, @Nullable IntPredicate statusCodeValidator) {
        this(api, rateLimiter, httpRequestBuilder, StatusCodeValidator.wrap(statusCodeValidator));
    }

    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request.Builder httpRequestBuilder, @Nullable StatusCodeValidator statusCodeValidator) {
        this(
                api,
                Objects.requireNonNull(httpRequestBuilder, "HTTP request builder may not be null").build(),
                statusCodeValidator
        );
    }

    @Deprecated
    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request.Builder httpRequestBuilder, @Nullable IntPredicate statusCodeValidator) {
        this(api, httpRequestBuilder, StatusCodeValidator.wrap(statusCodeValidator));
    }

    public PendingRequest(@Nonnull Reliqua api, @Nullable RateLimiter rateLimiter, @Nonnull Request.Builder httpRequestBuilder) {
        this(api, rateLimiter, httpRequestBuilder, null);
    }

    public PendingRequest(@Nonnull Reliqua api, @Nonnull Request.Builder httpRequestBuilder) {
        this(api, httpRequestBuilder, null);
    }

    public Reliqua getApi() {
        return api;
    }

    public Request getHttpRequest() {
        return httpRequest;
    }

    @Nullable
    protected abstract T onSuccess(@Nonnull Response response) throws IOException;

    protected void onError(@Nonnull RequestContext<T> context) throws IOException {
        Response response = context.getResponse();
        ResponseBody body = response.body();

        String s = "Server returned unexpected status code " + response.code() + (body == null ? "" : " Body: " + body.string());
        response.close();
        context.getErrorConsumer().accept(new RequestException(s, context.getCallStack()));
    }

    /**
     * Execute this request asynchronously. Cancelling the returned future has no effect.
     *
     * @return A future representing this request.
     */
    @Nonnull
    public Future<T> submit() {
        CompletableFuture<T> future = new CompletableFuture<>();
        async(future::complete, future::completeExceptionally);
        return future;
    }

    /**
     * Execute this request synchronously. The current thread is blocked until it completes.
     *
     * @return The response received from the API.
     */
    public T execute() {
        try {
            return submit().get();
        } catch(ExecutionException e) {
            throw new RequestException(e.getCause());
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestException(e);
        }
    }

    /**
     * Execute this request asynchronously, calling the appropriate callback when it's done.
     *
     * @param onSuccess Called when this request completes successfully.
     * @param onError Called when there's an error executing the request or parsing the response.
     */
    public void async(@Nullable Consumer<T> onSuccess, @Nullable Consumer<RequestException> onError) {
        StackTraceElement[] callSite = api.isTrackingCallSites() ? Thread.currentThread().getStackTrace() : null;
        if(onSuccess == null) onSuccess = v->{};
        if(onError == null) onError = Throwable::printStackTrace;

        Consumer<T> finalOnSuccess = onSuccess;
        Consumer<RequestException> finalOnError = onError;

        Runnable r = ()->
            api.getClient().newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                    finalOnError.accept(new RequestException(e, callSite));
                }

                @Override
                public void onResponse(@Nonnull Call call, @Nonnull Response response) {
                    try {
                        ResponseBody body = response.body();
                        if(!statusCodeValidator.test(response.code())) {
                            try {
                                onError(new RequestContext<>(
                                        callSite,
                                        finalOnSuccess,
                                        finalOnError,
                                        response
                                ));
                            } finally {
                                if(body != null) {
                                    body.close();
                                }
                            }
                            return;
                        }
                        try {
                            finalOnSuccess.accept(onSuccess(response));
                        } finally {
                            if(body != null) {
                                body.close();
                            }
                        }
                    } catch(RequestException e) {
                        finalOnError.accept(e);
                    } catch(Exception e) {
                        finalOnError.accept(new RequestException(e, callSite));
                    }
                }
            }
        );

        if(rateLimiter == null) {
            r.run();
        } else {
            rateLimiter.queue(r);
        }
    }

    /**
     * Execute this request asynchronously, calling the appropriate callback when it's done.
     *
     * @param onSuccess Called when this request completes successfully.
     */
    public void async(@Nullable Consumer<T> onSuccess) {
        async(onSuccess, null);
    }

    /**
     * Execute this request asynchronously.
     */
    public void async() {
        async(null, null);
    }
}
