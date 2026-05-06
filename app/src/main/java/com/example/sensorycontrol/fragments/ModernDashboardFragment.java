package com.example.sensorycontrol.fragments;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.models.HealthStatus;
import com.example.sensorycontrol.viewmodels.HealthMonitorViewModelWifi;
import com.example.sensorycontrol.wifi.WifiHealthMonitorManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import android.app.AlertDialog;
import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Modern Material You Dashboard Fragment
 * Professional medical UI with child-friendly touches
 */
public class ModernDashboardFragment extends Fragment {
    
    private HealthMonitorViewModelWifi viewModel;
    private String deviceIpAddress = "192.168.1.100"; // Default IP
    
    // UI Components
    private View statusDotLarge;
    private TextView tvHealthStatusText;
    private TextView tvConnectionBadge;
    private View connectionIndicator;
    
    // Vital Cards
    private MaterialCardView cardHeartRate, cardSpO2, cardTemperature;
    private TextView tvHeartRateValue, tvSpO2Value, tvTemperatureValue;
    private TextView tvHeartRateTrend, tvSpO2Trend, tvTemperatureTrend;
    private ImageView ivHeartPulse;
    
    // Info
    private TextView tvLastReading, tvBatteryLevel;
    
    // Buttons
    private MaterialButton btnStartMonitoring, btnAlertHistory;
    
    // Animation
    private Animation blinkAnimation;
    private Animation pulseAnimation;
    private Handler animationHandler = new Handler(Looper.getMainLooper());
    
    // Previous values for trend calculation
    private int previousHR = 0;
    private int previousSpO2 = 0;
    private float previousTemp = 0;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard_modern, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupViewModel();
        setupAnimations();
        setupButtons();
        observeData();
    }
    
    private void initViews(View view) {
        // Status indicator
        statusDotLarge = view.findViewById(R.id.status_dot_large);
        tvHealthStatusText = view.findViewById(R.id.tv_health_status_text);
        tvConnectionBadge = view.findViewById(R.id.tv_connection_badge);
        connectionIndicator = view.findViewById(R.id.connection_indicator);
        
        // Vital cards
        cardHeartRate = view.findViewById(R.id.card_heart_rate);
        cardSpO2 = view.findViewById(R.id.card_spo2);
        cardTemperature = view.findViewById(R.id.card_temperature);
        
        // Vital values
        tvHeartRateValue = view.findViewById(R.id.tv_heart_rate_value);
        tvSpO2Value = view.findViewById(R.id.tv_spo2_value);
        tvTemperatureValue = view.findViewById(R.id.tv_temperature_value);
        
        // Trend arrows
        tvHeartRateTrend = view.findViewById(R.id.tv_heart_rate_trend);
        tvSpO2Trend = view.findViewById(R.id.tv_spo2_trend);
        tvTemperatureTrend = view.findViewById(R.id.tv_temperature_trend);
        
        // Heart pulse icon
        ivHeartPulse = view.findViewById(R.id.iv_heart_pulse);
        
        // Info
        tvLastReading = view.findViewById(R.id.tv_last_reading);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        
        // Buttons
        btnStartMonitoring = view.findViewById(R.id.btn_start_monitoring);
        btnAlertHistory = view.findViewById(R.id.btn_alert_history);
    }
    
    private void setupViewModel() {
        viewModel = new ViewModelProvider(requireActivity()).get(HealthMonitorViewModelWifi.class);
    }
    
    private void setupAnimations() {
        // Blink animation for critical status
        blinkAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.blink_critical);
        
        // Pulse animation for heart icon
        pulseAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.pulse_scale);
    }
    
    private void setupButtons() {
        btnStartMonitoring.setOnClickListener(v -> {
            if (viewModel.isConnected()) {
                viewModel.disconnect();
            } else {
                // Show IP address input dialog
                showIpAddressDialog();
            }
        });
        
        btnAlertHistory.setOnClickListener(v -> {
            // Navigate to history
            Navigation.findNavController(v).navigate(R.id.historyFragment);
        });
    }
    
    private void showIpAddressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Enter ESP32 IP Address");
        
        final EditText input = new EditText(requireContext());
        input.setText(deviceIpAddress);
        input.setHint("192.168.1.100");
        builder.setView(input);
        
        builder.setPositiveButton("Connect", (dialog, which) -> {
            deviceIpAddress = input.getText().toString().trim();
            if (!deviceIpAddress.isEmpty()) {
                viewModel.connect(deviceIpAddress);
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void observeData() {
        // Observe connection state
        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            updateConnectionStatus(state);
        });
        
        // Observe heart rate
        viewModel.getHeartRate().observe(getViewLifecycleOwner(), hr -> {
            if (hr != null && hr > 0) {
                updateHeartRate(hr);
            }
        });
        
        // Observe SpO2
        viewModel.getSpO2().observe(getViewLifecycleOwner(), spo2 -> {
            if (spo2 != null && spo2 > 0) {
                updateSpO2(spo2);
            }
        });
        
        // Observe temperature
        viewModel.getTemperature().observe(getViewLifecycleOwner(), temp -> {
            if (temp != null && temp > 0) {
                updateTemperature(temp);
            }
        });
        
        // Observe health status
        viewModel.getHealthStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                updateHealthStatus(status);
            }
        });
        
        // Observe current reading for timestamp
        viewModel.getCurrentReading().observe(getViewLifecycleOwner(), reading -> {
            if (reading != null) {
                updateLastReading();
            }
        });
    }
    
    private void updateConnectionStatus(WifiHealthMonitorManager.ConnectionState state) {
        switch (state) {
            case CONNECTED:
                tvConnectionBadge.setText("Connected (WiFi)");
                connectionIndicator.setBackgroundResource(R.drawable.bg_status_dot_good);
                btnStartMonitoring.setText("Disconnect");
                btnStartMonitoring.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_close_clear_cancel, null));
                btnStartMonitoring.setEnabled(true);
                
                // Start heart pulse animation
                if (ivHeartPulse != null) {
                    ivHeartPulse.startAnimation(pulseAnimation);
                }
                break;
                
            case CONNECTING:
                tvConnectionBadge.setText("Connecting...");
                connectionIndicator.setBackgroundResource(R.drawable.bg_status_dot_warning);
                btnStartMonitoring.setText("Connecting...");
                btnStartMonitoring.setEnabled(false);
                break;
                
            case DISCONNECTED:
                tvConnectionBadge.setText("Disconnected");
                connectionIndicator.setBackgroundResource(R.drawable.bg_status_dot_warning);
                btnStartMonitoring.setText("Connect Device");
                btnStartMonitoring.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_search, null));
                btnStartMonitoring.setEnabled(true);
                
                // Stop heart pulse animation
                if (ivHeartPulse != null) {
                    ivHeartPulse.clearAnimation();
                }
                break;
                
            case ERROR:
                tvConnectionBadge.setText("Connection Error");
                connectionIndicator.setBackgroundResource(R.drawable.bg_status_dot_critical);
                btnStartMonitoring.setText("Retry Connection");
                btnStartMonitoring.setEnabled(true);
                
                // Stop heart pulse animation
                if (ivHeartPulse != null) {
                    ivHeartPulse.clearAnimation();
                }
                break;
        }
    }
    
    private void updateHeartRate(int hr) {
        // Animate value change
        animateValueChange(tvHeartRateValue, String.valueOf(hr));
        
        // Update trend
        if (previousHR > 0) {
            updateTrend(tvHeartRateTrend, hr, previousHR);
        }
        previousHR = hr;
        
        // Update card border based on status
        updateCardStatus(cardHeartRate, evaluateHeartRate(hr));
    }
    
    private void updateSpO2(int spo2) {
        // Animate value change
        animateValueChange(tvSpO2Value, String.valueOf(spo2));
        
        // Update trend
        if (previousSpO2 > 0) {
            updateTrend(tvSpO2Trend, spo2, previousSpO2);
        }
        previousSpO2 = spo2;
        
        // Update card border based on status
        updateCardStatus(cardSpO2, evaluateSpO2(spo2));
    }
    
    private void updateTemperature(float temp) {
        // Animate value change
        animateValueChange(tvTemperatureValue, String.format(Locale.US, "%.1f", temp));
        
        // Update trend
        if (previousTemp > 0) {
            updateTrend(tvTemperatureTrend, temp, previousTemp);
        }
        previousTemp = temp;
        
        // Update card border based on status
        updateCardStatus(cardTemperature, evaluateTemperature(temp));
    }
    
    private void updateHealthStatus(HealthStatus status) {
        HealthStatus.Condition condition = status.getOverallCondition();
        
        switch (condition) {
            case GOOD:
                statusDotLarge.setBackgroundResource(R.drawable.bg_status_dot_good);
                statusDotLarge.clearAnimation();
                tvHealthStatusText.setText("All Vitals Normal");
                tvHealthStatusText.setTextColor(getResources().getColor(R.color.status_good, null));
                break;
                
            case WARNING:
                statusDotLarge.setBackgroundResource(R.drawable.bg_status_dot_warning);
                statusDotLarge.clearAnimation();
                tvHealthStatusText.setText("Attention Needed");
                tvHealthStatusText.setTextColor(getResources().getColor(R.color.status_warning, null));
                break;
                
            case CRITICAL:
                statusDotLarge.setBackgroundResource(R.drawable.bg_status_dot_critical);
                statusDotLarge.startAnimation(blinkAnimation);
                tvHealthStatusText.setText("CRITICAL");
                tvHealthStatusText.setTextColor(getResources().getColor(R.color.status_critical, null));
                break;
        }
    }
    
    private void updateLastReading() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        tvLastReading.setText("Last reading: " + sdf.format(new Date()));
    }
    
    private void animateValueChange(TextView textView, String newValue) {
        // Scale animation
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(textView, "scaleX", 1.0f, 1.1f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(textView, "scaleY", 1.0f, 1.1f, 1.0f);
        scaleX.setDuration(300);
        scaleY.setDuration(300);
        scaleX.start();
        scaleY.start();
        
        textView.setText(newValue);
    }
    
    private void updateTrend(TextView trendView, float current, float previous) {
        if (current > previous) {
            trendView.setText("↑");
            trendView.setTextColor(getResources().getColor(R.color.status_critical, null));
            trendView.setVisibility(View.VISIBLE);
        } else if (current < previous) {
            trendView.setText("↓");
            trendView.setTextColor(getResources().getColor(R.color.status_good, null));
            trendView.setVisibility(View.VISIBLE);
        } else {
            trendView.setVisibility(View.GONE);
        }
    }
    
    private void updateTrend(TextView trendView, int current, int previous) {
        updateTrend(trendView, (float) current, (float) previous);
    }
    
    private void updateCardStatus(MaterialCardView card, HealthStatus.VitalStatus status) {
        switch (status) {
            case GOOD:
                card.setStrokeColor(getResources().getColor(R.color.status_good, null));
                card.setStrokeWidth(4);
                break;
            case WARNING:
                card.setStrokeColor(getResources().getColor(R.color.status_warning, null));
                card.setStrokeWidth(4);
                break;
            case CRITICAL:
                card.setStrokeColor(getResources().getColor(R.color.status_critical, null));
                card.setStrokeWidth(4);
                break;
        }
    }
    
    // Evaluation methods (simplified - use HealthStatus for full logic)
    private HealthStatus.VitalStatus evaluateHeartRate(int hr) {
        if (hr < 40 || hr > 150) return HealthStatus.VitalStatus.CRITICAL;
        if (hr < 60 || hr > 120) return HealthStatus.VitalStatus.WARNING;
        return HealthStatus.VitalStatus.GOOD;
    }
    
    private HealthStatus.VitalStatus evaluateSpO2(int spo2) {
        if (spo2 < 90) return HealthStatus.VitalStatus.CRITICAL;
        if (spo2 < 95) return HealthStatus.VitalStatus.WARNING;
        return HealthStatus.VitalStatus.GOOD;
    }
    
    private HealthStatus.VitalStatus evaluateTemperature(float temp) {
        if (temp >= 38.5f) return HealthStatus.VitalStatus.CRITICAL;
        if (temp > 37.5f || temp < 36.0f) return HealthStatus.VitalStatus.WARNING;
        return HealthStatus.VitalStatus.GOOD;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up animations
        if (statusDotLarge != null) {
            statusDotLarge.clearAnimation();
        }
        if (ivHeartPulse != null) {
            ivHeartPulse.clearAnimation();
        }
        animationHandler.removeCallbacksAndMessages(null);
    }
}
