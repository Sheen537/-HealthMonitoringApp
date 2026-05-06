package com.example.sensorycontrol.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.auth.AuthManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Login screen - Email/Password and Google authentication
 */
public class LoginActivity extends AppCompatActivity {
    
    private static final String TAG = "LoginActivity";
    
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton googleSignInButton;
    private TextView signupLink;
    private ProgressBar progressBar;
    
    private AuthManager authManager;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private AlertDialog loadingDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        authManager = AuthManager.getInstance();
        
        // Initialize views
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        googleSignInButton = findViewById(R.id.google_sign_in_button);
        signupLink = findViewById(R.id.signup_link);
        progressBar = findViewById(R.id.progress_bar);
        
        // Setup Google Sign-In
        setupGoogleSignIn();
        
        // Login button click
        loginButton.setOnClickListener(v -> attemptLogin());
        
        // Signup link click
        signupLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }
    
    private void setupGoogleSignIn() {
        try {
            // Get Web Client ID from google-services.json automatically
            String webClientId = getString(R.string.default_web_client_id);
            
            authManager.initializeGoogleSignIn(this, webClientId);
            
            // Setup Google Sign-In launcher
            googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignInResult(task);
                }
            );
            
            // Google Sign-In button click
            googleSignInButton.setOnClickListener(v -> {
                showProgress(true);
                Intent signInIntent = authManager.getGoogleSignInClient().getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
            
            Log.d(TAG, "Google Sign-In initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Google Sign-In setup failed", e);
            Toast.makeText(this, "Google Sign-In setup failed. Please check Firebase configuration.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            showLoadingDialog("Signing in with Google...", "Authenticating your account");
            
            authManager.signInWithGoogle(completedTask, new AuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        showProgress(false);
                        showSuccessDialog("Welcome Back!", "You've successfully signed in with Google.", true);
                    });
                }
                
                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        showProgress(false);
                        showErrorDialog("Google Sign-In Failed", getUserFriendlyError(error));
                        Log.e(TAG, "Google Sign-In failed: " + error);
                    });
                }
            });
        } catch (ApiException e) {
            runOnUiThread(() -> {
                dismissLoadingDialog();
                showProgress(false);
            });
            Log.e(TAG, "Google sign in failed", e);
            
            String errorMessage = "Please try again.";
            if (e.getStatusCode() == 12501) {
                errorMessage = "Sign-in was cancelled. Please try again.";
            } else if (e.getStatusCode() == 7) {
                errorMessage = "Network error. Please check your internet connection.";
            } else if (e.getStatusCode() == 10) {
                errorMessage = "Google Sign-In configuration error. Please add your SHA-1 fingerprint to Firebase Console.";
            }
            showErrorDialog("Google Sign-In Failed", errorMessage);
        }
    }
    
    private void attemptLogin() {
        // Reset errors
        emailInput.setError(null);
        passwordInput.setError(null);
        
        // Get values
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // Validate
        boolean cancel = false;
        View focusView = null;
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            focusView = passwordInput;
            cancel = true;
        } else if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            focusView = passwordInput;
            cancel = true;
        }
        
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            focusView = emailInput;
            cancel = true;
        } else if (!isEmailValid(email)) {
            emailInput.setError("Please enter a valid email address");
            focusView = emailInput;
            cancel = true;
        }
        
        if (cancel) {
            if (focusView != null) {
                focusView.requestFocus();
            }
            return;
        }
        
        // Show progress
        showProgress(true);
        showLoadingDialog("Signing In", "Verifying your credentials...");
        
        // Attempt login
        authManager.signIn(email, password, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showProgress(false);
                    showSuccessDialog("Welcome Back!", "You've successfully signed in.", true);
                });
            }
            
            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showProgress(false);
                    String userMessage = getUserFriendlyError(error);
                    showErrorDialog("Login Failed", userMessage);
                    Log.e(TAG, "Login failed: " + error);
                    
                    // Set field-specific errors
                    String lowerError = error.toLowerCase();
                    if (lowerError.contains("password")) {
                        passwordInput.setError("Incorrect password");
                        passwordInput.requestFocus();
                    } else if (lowerError.contains("email") || lowerError.contains("user")) {
                        emailInput.setError("Account not found");
                        emailInput.requestFocus();
                    }
                });
            }
        });
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private String getUserFriendlyError(String error) {
        if (error == null) return "Login failed. Please try again.";
        
        String lowerError = error.toLowerCase();
        if (lowerError.contains("password")) {
            return "Incorrect password. Please try again.";
        } else if (lowerError.contains("user") || lowerError.contains("email") || lowerError.contains("no user record")) {
            return "No account found with this email.";
        } else if (lowerError.contains("network")) {
            return "Network error. Check your internet connection.";
        } else if (lowerError.contains("disabled")) {
            return "This account has been disabled.";
        }
        return "Login failed: " + error;
    }
    
    private boolean isEmailValid(String email) {
        return email.contains("@") && email.contains(".");
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        googleSignInButton.setEnabled(!show);
        emailInput.setEnabled(!show);
        passwordInput.setEnabled(!show);
    }
    
    private void showLoadingDialog(String title, String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView messageView = dialogView.findViewById(R.id.dialog_message);
        
        titleView.setText(title);
        messageView.setText(message);
        
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        loadingDialog = builder.create();
        loadingDialog.show();
    }
    
    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
    
    private void showSuccessDialog(String title, String message, boolean navigateAfter) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Continue", (dialog, which) -> {
                dialog.dismiss();
                if (navigateAfter) {
                    navigateToMain();
                }
            })
            .setCancelable(false)
            .show();
    }
    
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissLoadingDialog();
    }
}
