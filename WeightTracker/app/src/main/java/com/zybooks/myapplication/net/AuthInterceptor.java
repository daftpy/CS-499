package com.zybooks.myapplication.net;

import android.content.Context;

import com.zybooks.myapplication.auth.AuthStore;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationService;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

// Intercepts HTTP requests from inside the app and adds the access token.
// Refreshes access token if necessary
public class AuthInterceptor implements Interceptor, Closeable {
    private static final long TIMEOUT_SECONDS = 15; // refresh wait ceiling
    private final Context app;

    // AppAuth service used for token refresh / retrieval
    private final AuthorizationService authService;

    // Secure persistence of AuthState
    private final AuthStore store;

    public AuthInterceptor(Context ctx) throws Exception {
        this.app = ctx.getApplicationContext();
        this.authService = new AuthorizationService(app);
        this.store = new AuthStore(app);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request req = chain.request();

        // Read the latest auth state (may be null if user is signed out)
        AuthState state = store.read();
        if (state == null || !state.isAuthorized()) {
            // No token available? Just continue; server can reject.
            return chain.proceed(req);
        }

        // Refresh/get token synchronously
        final String[] tokenHolder = new String[1];
        final AuthorizationException[] errorHolder = new AuthorizationException[1];
        CountDownLatch latch = new CountDownLatch(1);

        // Attempts the request, with a fresh access token provided
        state.performActionWithFreshTokens(authService, (accessToken, idToken, ex) -> {
            if (ex != null) {
                // Refresh error; proceed without header (server will 401)
                errorHolder[0] = ex;
            } else {
                tokenHolder[0] = accessToken;
                // Persist any updated state (e.g., new refresh token)
                try { store.write(state); } catch (Exception ignored) {}
            }
            latch.countDown();
        });

        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Attach Authorization header if we have a token
        if (tokenHolder[0] != null) {
            req = req.newBuilder()
                    .addHeader("Authorization", "Bearer " + tokenHolder[0])
                    .build();
        }
        // If token missing/failed, request continues without header.
        return chain.proceed(req);
    }

    // Destroy auth service
    @Override public void close() {
        authService.dispose();
    }
}
