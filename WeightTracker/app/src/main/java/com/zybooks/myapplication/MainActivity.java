package com.zybooks.myapplication;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

/*
    Alexander Flood
    SNHU CS-360
    22nd June 2025
 */

/**
 * MainActivity
 * - Displays the title of the app and allows the user to
 * - register or login
 */
public class MainActivity extends AppCompatActivity {

    // This is the realm URI for the weight tracker API we will authenticate against
    private static final String ISSUER =
            "https://keycloak.10-0-2-2.sslip.io/realms/WeightTrackerAPI";

    // The name of the the keycloak client to use
    private static final String CLIENT_ID = "WeightTrackerMobileClient";

    // Redirect URI inside this android application
    private static final Uri REDIRECT_URI =
            Uri.parse("com.zybooks.myapplication:/oauth2redirect");

    private AuthorizationService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Boilerplate from AndroidStudio
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Instantiate a new auth service
        authService = new AuthorizationService(this);
        // Get the login button
        Button btn = findViewById(R.id.sign_in_button);

        // Setup the button onClick listener
        btn.setOnClickListener(v ->
            AuthorizationServiceConfiguration.fetchFromIssuer(
                Uri.parse(ISSUER),
                (config, ex) -> {
                    if (ex != null) {
                        ex.printStackTrace(); // minimal error handling for now
                        return;
                    }

                    // Create the authorization request
                    AuthorizationRequest request = new AuthorizationRequest.Builder(
                            config, CLIENT_ID, ResponseTypeValues.CODE, REDIRECT_URI)
                            .setScopes("openid", "profile", "email")
                            .build();

                    // Create the intent
                    Intent onComplete = new Intent(this, AuthCallbackActivity.class);
                    PendingIntent completed = PendingIntent.getActivity(
                            this, 0, onComplete, PendingIntent.FLAG_MUTABLE);

                    // Perform the authorization and navigate to the pending intent
                    authService.performAuthorizationRequest(request, completed);
                })
        );
    }

    @Override
    protected void onDestroy() {
        // Destroy the auth service
        if (authService != null) authService.dispose();
        super.onDestroy();
    }
}