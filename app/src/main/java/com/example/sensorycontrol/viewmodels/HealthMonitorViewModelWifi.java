package com.example.sensorycontrol.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.sensorycontrol.models.HealthReading;
import com.example.sensorycontrol.models.HealthStatus;
import com.example.sensorycontrol.repository.FirestoreHealthRepository;
import com.example.sensorycontrol.wifi.WifiHealthMonitorManager;

import java.util.List;

import timber.log.Timber;

/**
 * ViewModel for Health Monitoring with WiFi + Firestore
 * Replaces BLE-based HealthMonitorViewModel
 * 
 * Changes:
 * - Uses WifiHealthMonitorManager instead of HealthMonitorBleManager
 * - Uses FirestoreHealthRepository instead of Room-based repository
 * - Maintains same LiveData interface for UI compatibility
 * 
 * MVVM Architecture maintained - UI layer unchanged
 */
public class HealthMonitorViewModelWifi extends AndroidViewModel {
    
    private final WifiHealthMonitorManager wifiManager;
    private final FirestoreHealthRepository repository;
    
    // LiveData for UI observation (UNCHANGED - maintains compatibility)
    private final MutableLiveData<HealthReading> currentReading = new MutableLiveData<>();
    private final MutableLiveData<HealthStatus> healthStatus = new MutableLiveData<>();
    private final MutableLiveData<WifiHealthMonitorManager.ConnectionState> connectionState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> deviceIpAddress = new MutableLiveData<>();
    
    // Individual vital LiveData for easier UI binding
    private final MutableLiveData<Integer> heartRate = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> spO2 = new MutableLiveData<>(0);
    private final MutableLiveData<Float> temperature = new MutableLiveData<>(0.0f);
    private final MutableLiveData<Boolean> hrValid = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> tempValid = new MutableLiveData<>(false);
    
    // Data storage control (same strategy as BLE version)
    private long lastStoredTimestamp = 0;
    private static final long STORAGE_INTERVAL_MS = 10000; // Store every 10 seconds
    private HealthStatus.Condition lastStoredStatus = null;
    
    public HealthMonitorViewModelWifi(@NonNull Application application) {
        super(application);
        
        wifiManager = new WifiHealthMonitorManager(application);
        wifiManager.setWifiCallback(wifiCallback);
        
        // Initialize Firestore repository
        repository = new FirestoreHealthRepository();
        
        // Initialize connection state
        connectionState.setValue(WifiHealthMonitorManager.ConnectionState.DISCONNECTED);
        
        // Initialize health status
        healthStatus.setValue(new HealthStatus());
    }
    
    /**
     * WiFi callback implementation
     */
    private final WifiHealthMonitorManager.WifiCallback wifiCallback = new WifiHealthMonitorManager.WifiCallback() {
        
        @Override
        public void onConnectionStateChanged(WifiHealthMonitorManager.ConnectionState state) {
            connectionState.postValue(state);
            
            if (state == WifiHealthMonitorManager.ConnectionState.DISCONNECTED) {
                // Reset values on disconnect
                resetValues();
            }
        }
        
        @Override
        public void onHealthDataReceived(HealthReading reading) {
            // Update current reading
            currentReading.postValue(reading);
            
            // Update individual vitals
            if (reading.isHrValid()) {
                heartRate.postValue(reading.getHeartRate());
                spO2.postValue(reading.getSpO2());
                hrValid.postValue(true);
            } else {
                hrValid.postValue(false);
            }
            
            if (reading.isTempValid()) {
                temperature.postValue(reading.getTemperature());
                tempValid.postValue(true);
            } else {
                tempValid.postValue(false);
            }
            
            // Evaluate and update health status
            HealthStatus status = HealthStatus.evaluate(reading);
            healthStatus.postValue(status);
            
            // Store data to Firestore based on strategy
            storeHealthDataIfNeeded(reading, status);
            
            Timber.d("Health data updated: %s", reading.toString());
            Timber.d("Health status: %s", status.toString());
        }
        
        @Override
        public void onError(String error) {
            errorMessage.postValue(error);
            Timber.e("WiFi Error: %s", error);
        }
    };
    
    /**
     * Connect to ESP32 device via WiFi
     * 
     * @param ipAddress ESP32 IP address (e.g., "192.168.1.100")
     */
    public void connect(String ipAddress) {
        wifiManager.connect(ipAddress);
        deviceIpAddress.setValue(ipAddress);
    }
    
    /**
     * Connect with custom port
     */
    public void connect(String ipAddress, int port) {
        wifiManager.connect(ipAddress, port);
        deviceIpAddress.setValue(ipAddress);
    }
    
    /**
     * Disconnect from device
     */
    public void disconnect() {
        wifiManager.disconnect();
    }
    
    /**
     * Send control command to device
     * 
     * @param command Command type ("start", "stop", "status")
     */
    public void sendCommand(String command) {
        wifiManager.sendCommand(command);
    }
    
    /**
     * Reset all values to default
     */
    private void resetValues() {
        heartRate.postValue(0);
        spO2.postValue(0);
        temperature.postValue(0.0f);
        hrValid.postValue(false);
        tempValid.postValue(false);
        currentReading.postValue(null);
        healthStatus.postValue(new HealthStatus());
    }
    
    /**
     * Clear error message
     */
    public void clearError() {
        errorMessage.setValue(null);
    }
    
    /**
     * Store health data to Firestore based on strategy:
     * 1. Every 10 seconds (configurable)
     * 2. When health status changes
     * 
     * This prevents storing every packet while ensuring critical data is captured.
     * SAME STRATEGY as BLE version - maintains data efficiency
     */
    private void storeHealthDataIfNeeded(HealthReading reading, HealthStatus status) {
        long currentTime = System.currentTimeMillis();
        HealthStatus.Condition currentStatus = status.getOverallCondition();
        
        // Store if:
        // 1. Enough time has passed since last storage
        // 2. Health status has changed (especially to WARNING or CRITICAL)
        boolean shouldStore = false;
        
        if (currentTime - lastStoredTimestamp >= STORAGE_INTERVAL_MS) {
            shouldStore = true;
            Timber.d("Storing data: Time interval reached");
        } else if (lastStoredStatus != currentStatus) {
            shouldStore = true;
            Timber.d("Storing data: Status changed from %s to %s", lastStoredStatus, currentStatus);
        }
        
        if (shouldStore && reading.isHrValid() && reading.isTempValid()) {
            repository.insertHealthRecord(reading, status);
            lastStoredTimestamp = currentTime;
            lastStoredStatus = currentStatus;
        }
    }
    
    // Repository methods for accessing historical data (Firestore)
    
    /**
     * Get all health records for the current user.
     */
    public LiveData<List<FirestoreHealthRepository.HealthRecordFirestore>> getAllRecords() {
        return repository.getAllRecords();
    }
    
    /**
     * Get the last N health records.
     */
    public LiveData<List<FirestoreHealthRepository.HealthRecordFirestore>> getLastNRecords(int limit) {
        return repository.getLastNRecords(limit);
    }
    
    /**
     * Get health records within a time range.
     */
    public LiveData<List<FirestoreHealthRepository.HealthRecordFirestore>> getRecordsByTimeRange(long startTime, long endTime) {
        return repository.getRecordsByTimeRange(startTime, endTime);
    }
    
    /**
     * Get records with critical health status.
     */
    public LiveData<List<FirestoreHealthRepository.HealthRecordFirestore>> getCriticalRecords() {
        return repository.getCriticalRecords();
    }
    
    /**
     * Get the total count of records.
     */
    public void getRecordCount(FirestoreHealthRepository.CountCallback callback) {
        repository.getRecordCount(callback);
    }
    
    /**
     * Delete all records for the current user.
     */
    public void deleteAllRecords() {
        repository.deleteAllRecords();
    }
    
    /**
     * Delete records older than specified days.
     */
    public void deleteOldRecords(int days) {
        repository.deleteOldRecords(days);
    }
    
    /**
     * Get average vital signs over a time period.
     */
    public void getAverageVitals(long startTime, long endTime, 
                                 FirestoreHealthRepository.AverageVitalsCallback callback) {
        repository.getAverageVitals(startTime, endTime, callback);
    }
    
    // LiveData getters for UI observation (UNCHANGED - maintains compatibility)
    
    public LiveData<HealthReading> getCurrentReading() {
        return currentReading;
    }
    
    public LiveData<HealthStatus> getHealthStatus() {
        return healthStatus;
    }
    
    public LiveData<WifiHealthMonitorManager.ConnectionState> getConnectionState() {
        return connectionState;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<String> getDeviceIpAddress() {
        return deviceIpAddress;
    }
    
    public LiveData<Integer> getHeartRate() {
        return heartRate;
    }
    
    public LiveData<Integer> getSpO2() {
        return spO2;
    }
    
    public LiveData<Float> getTemperature() {
        return temperature;
    }
    
    public LiveData<Boolean> getHrValid() {
        return hrValid;
    }
    
    public LiveData<Boolean> getTempValid() {
        return tempValid;
    }
    
    /**
     * Check if currently connected
     */
    public boolean isConnected() {
        return wifiManager.isConnected();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        wifiManager.close();
        repository.shutdown();
        Timber.d("ViewModel cleared, WiFi manager and repository closed");
    }
}
