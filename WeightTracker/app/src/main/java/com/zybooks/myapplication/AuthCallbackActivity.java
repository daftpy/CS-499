package com.zybooks.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.myapplication.auth.AuthStore;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

/**
 * AuthCallbackActivity
 * - Receives the redirect from the IdP
 * - Exchanges the auth code for tokens
 * - Persists the AuthState and navigates to the main screen
 */
public class AuthCallbackActivity extends AppCompatActivity {

    private AuthorizationService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // One AuthorizationService per Activity lifecycle
        authService = new AuthorizationService(this);

        // Parse redirect intent for success (response) or failure (exception)
        AuthorizationResponse resp = AuthorizationResponse.fromIntent(getIntent());
        AuthorizationException err = AuthorizationException.fromIntent(getIntent());

        // If provider returned an error, show it and exit
        if (err != null) {
            Toast.makeText(this, "Auth error: " + err.errorDescription, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // If we have a successful authorization response, exchange code for tokens
        if (resp != null) {
            TokenRequest tokenReq = resp.createTokenExchangeRequest();

            // Perform the code→token exchange
            authService.performTokenRequest(tokenReq, (TokenResponse token, AuthorizationException ex) -> {
                if (ex != null) {
                    // Bubble up a useful message (error id + optional description)
                    String msg = "Token error: " + ex.error +
                            (ex.errorDescription != null ? " – " + ex.errorDescription : "");
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                // Persist the new AuthState (contains access/refresh tokens)
                try {
                    AuthState state = new AuthState(resp, null);
                    state.update(token, null);
                    new AuthStore(this).write(state); // secure storage
                } catch (Exception e) {
                    Toast.makeText(this, "Save error", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                // Success: go to the main screen
                startActivity(new Intent(this, GridActivity.class));
                finish();
            });
        } else {
            // No response and no error: nothing to do
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // Clean up AuthorizationService resources
        if (authService != null) authService.dispose();
        super.onDestroy();
    }
}
