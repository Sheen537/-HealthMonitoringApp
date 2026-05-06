package com.example.sensorycontrol.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.auth.AuthManager;

/**
 * Splash screen - checks authentication status and routes accordingly
 */
public class SplashActivity extends AppCompatActivity {
    
    private static final int SPLASH_DELAY = 2000; // 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Check auth status after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AuthManager authManager = AuthManager.getInstance();
            
            Intent intent;
            if (authManager.isUserLoggedIn()) {
                // User is logged in, go to main app
                intent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                // User not logged in, go to login
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            
            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}
