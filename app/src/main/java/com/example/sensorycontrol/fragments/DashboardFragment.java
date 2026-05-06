package com.example.sensorycontrol.fragments;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.activities.LoginActivity;
import com.example.sensorycontrol.auth.AuthManager;
import com.example.sensorycontrol.wifi.WifiHealthMonitorManager;
import com.example.sensorycontrol.models.HealthStatus;
import com.example.sensorycontrol.viewmodels.HealthMonitorViewModelWifi;
import com.google.firebase.auth.FirebaseUser;

/**
 * Dashboard - Shows real-time health data with three-dot status indicator
 * Phase 5 Implementation
 */
public class DashboardFragment extends Fragment {
    
    private HealthMonitorViewModelWifi viewModel;
    
    // UI Components
    private TextView welcomeText;
    private TextView heartRateValue;
    private TextView spo2Value;
    private TextView temperatureValue;
    private TextView connectionStatusText;
    private TextView healthStatusText;
    private Button connectButton;
    private Button disconnectButton;
    private Button logoutButton;
    
    // Three-dot health indicator
    private View healthDot1;
    private View healthDot2;
    private View healthDot3;
    
    // Blinking animation for critical status
    private Animation blinkAnimation;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(HealthMonitorViewModelWifi.class);
        
        // Initialize views
        initializeViews(view);
        
        // Setup observers
        setupObservers();
        
        // Setup buttons
        setupButtons();
        
        // Load user info
        loadUserInfo();
        
        // Setup blinking animation
        setupBlinkAnimation();
    }
    
    private void initializeViews(View view) {
        welcomeText = view.findViewById(R.id.welcome_text);
        heartRateValue = view.findViewById(R.id.heart_rate_value);
        spo2Value = view.findViewById(R.id.spo2_value);
        temperatureValue = view.findViewById(R.id.temperature_value);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        healthStatusText = view.findViewById(R.id.health_status_text);
        connectButton = view.findViewById(R.id.connect_button);
        disconnectButton = view.findViewById(R.id.disconnect_button);
        logoutButton = view.findViewById(R.id.logout_button);
        
        // Three-dot health indicator
        healthDot1 = view.findViewById(R.id.health_dot_1);
        healthDot2 = view.findViewById(R.id.health_dot_2);
        healthDot3 = view.findViewById(R.id.health_dot_3);
        
        // Set initial values
        heartRateValue.setText("--");
        spo2Value.setText("--");
        temperatureValue.setText("--");
        connectionStatusText.setText("Not connected");
        healthStatusText.setText("Waiting for data...");
    }
    
    private void setupObservers() {
        // Observe heart rate
        viewModel.getHeartRate().observe(getViewLifecycleOwner(), hr -> {
            if (viewModel.getHrValid().getValue() != null && viewModel.getHrValid().getValue()) {
                heartRateValue.setText(String.valueOf(hr));
            } else {
                heartRateValue.setText("--");
            }
        });
        
        // Observe SpO2
        viewModel.getSpO2().observe(getViewLifecycleOwner(), spo2 -> {
            if (viewModel.getHrValid().getValue() != null && viewModel.getHrValid().getValue()) {
                spo2Value.setText(String.valueOf(spo2));
            } else {
                spo2Value.setText("--");
            }
        });
        
        // Observe temperature
        viewModel.getTemperature().observe(getViewLifecycleOwner(), temp -> {
            if (viewModel.getTempValid().getValue() != null && viewModel.getTempValid().getValue()) {
                temperatureValue.setText(String.format("%.1f", temp));
            } else {
                temperatureValue.setText("--");
            }
        });
        
        // Observe connection state
        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            updateConnectionStatus(state);
        });
        
        // Observe health status
        viewModel.getHealthStatus().observe(getViewLifecycleOwner(), status -> {
            updateHealthIndicator(status);
        });
        
        // Observe errors
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });
    }
    
    private void setupButtons() {
        connectButton.setOnClickListener(v -> {
            if (!viewModel.isBluetoothEnabled()) {
                Toast.makeText(requireContext(), "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.startScan();
            Toast.makeText(requireContext(), "Scanning for device...", Toast.LENGTH_SHORT).show();
        });
        
        disconnectButton.setOnClickListener(v -> {
            viewModel.disconnect();
            Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show();
        });
        
        logoutButton.setOnClickListener(v -> {
            performLogout();
        });
    }
    
    private void updateConnectionStatus(HealthMonitorBleManager.ConnectionState state) {
        switch (state) {
            case DISCONNECTED:
                connectionStatusText.setText("Not connected");
                connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                break;
                
            case SCANNING:
                connectionStatusText.setText("Scanning...");
                connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(false);
                break;
                
            case CONNECTING:
                connectionStatusText.setText("Connecting...");
                connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(false);
                break;
                
            case CONNECTED:
                connectionStatusText.setText("Connected");
                connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                break;
                
            case DISCONNECTING:
                connectionStatusText.setText("Disconnecting...");
                connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(false);
                break;
        }
    }
    
    /**
     * Update three-dot health indicator based on health status
     */
    private void updateHealthIndicator(HealthStatus status) {
        if (status == null) {
            return;
        }
        
        // Stop any existing animations
        stopBlinkAnimation();
        
        // Get color based on condition
        int color = ContextCompat.getColor(requireContext(), status.getConditionColor());
        
        // Update all three dots with the same color
        healthDot1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        healthDot2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        healthDot3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        
        // Update status text
        healthStatusText.setText(status.getStatusMessage());
        healthStatusText.setTextColor(color);
        
        // Start blinking animation if critical
        if (status.shouldBlink()) {
            startBlinkAnimation();
        }
    }
    
    /**
     * Setup blinking animation for critical status
     */
    private void setupBlinkAnimation() {
        blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(500);  // 500ms fade out
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
    }
    
    /**
     * Start blinking animation on health dots
     */
    private void startBlinkAnimation() {
        if (blinkAnimation != null && healthDot1 != null) {
            healthDot1.startAnimation(blinkAnimation);
            healthDot2.startAnimation(blinkAnimation);
            healthDot3.startAnimation(blinkAnimation);
        }
    }
    
    /**
     * Stop blinking animation
     */
    private void stopBlinkAnimation() {
        if (healthDot1 != null) {
            healthDot1.clearAnimation();
            healthDot2.clearAnimation();
            healthDot3.clearAnimation();
            
            // Reset alpha to fully visible
            healthDot1.setAlpha(1.0f);
            healthDot2.setAlpha(1.0f);
            healthDot3.setAlpha(1.0f);
        }
    }
    
    private void loadUserInfo() {
        AuthManager authManager = AuthManager.getInstance();
        FirebaseUser user = authManager.getCurrentUser();
        
        if (user != null) {
            authManager.getUserProfile(new AuthManager.ProfileCallback() {
                @Override
                public void onSuccess(java.util.Map<String, Object> profile) {
                    String name = (String) profile.get("name");
                    if (name != null && !name.isEmpty()) {
                        welcomeText.setText("Welcome, " + name + "!");
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    // Use email as fallback
                    if (user.getEmail() != null) {
                        welcomeText.setText("Welcome!");
                    }
                }
            });
        }
    }
    
    private void performLogout() {
        // Disconnect from device first
        if (viewModel.isConnected()) {
            viewModel.disconnect();
        }
        
        // Sign out
        AuthManager.getInstance().signOut(requireContext());
        
        // Navigate to login screen
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopBlinkAnimation();
    }
}
